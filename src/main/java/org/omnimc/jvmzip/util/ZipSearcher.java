/*
 * MIT License
 *
 * Copyright (c) 2025 OmniMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.omnimc.jvmzip.util;

import org.omnimc.jvmzip.entry.ZipEntry;
import org.omnimc.jvmzip.throwables.CentralDirectoryException;
import org.omnimc.jvmzip.throwables.InvalidZipFileException;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.function.Function;

import static org.omnimc.jvmzip.util.LittleEndian.toInt4;
import static org.omnimc.jvmzip.util.LittleEndian.toShort2;

/**
 * A Simple Utility class in efforts of making jvmzip a lot cleaner.
 * <p>
 * Everything in here follows the specification outlined by <a href="https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT">APPNOTE.TXT</a>.
 * However, tries to optimize the approach without having to go down to native code.
 *
 * @author <a href=https://github.com/CadenCCC>Caden</a>
 * @since 1.0.0
 */
public final class ZipSearcher {

    /**
     * This is the 4 byte header for a LocalFileEntry.
     */
    public static final int LOCAL_FILE_HEADER = 0x04034b50;
    /**
     * This is the 4 byte header for a CentralDirectoryEntry.
     */
    public static final int CENTRAL_DIRECTORY_HEADER = 0x02014b50;
    /**
     * This is the Zip32 4 byte header for an EndOfCentralDirectory.
     */
    public static final int END_OF_CENTRAL_DIRECTORY = 0x06054b50;

    /**
     * This is the universal buffer for the CD, we do this for optimization.
     */
    private static final byte[] centralDirectoryBuffer = new byte[46];
    /**
     * This is the universal buffer for the LF, we do this for optimization.
     */
    private static final byte[] localFileBuffer = new byte[30];
    /**
     * This is the universal 4 byte buffer so we can more effectively look for the EOCD Header.
     */
    private static final byte[] endOfCentralDirectorySigBuffer = new byte[4];

    /**
     * Utility method that cycles through the CentralDirectory headers.
     * <p>
     *     We take an original offset, and use a {@linkplain RandomAccessFile} to read the offset.
     *     If the offset does not represent a {@linkplain ZipSearcher#CENTRAL_DIRECTORY_HEADER} then it will return {@code null}.
     * <p>
     *     If the offset does represent a {@linkplain ZipSearcher#CENTRAL_DIRECTORY_HEADER} then it will process it and call a {@linkplain Function}
     *     provided by a parameter.
     *     If the {@linkplain Function} returns {@code null} it will then go on to the next Central Directory Header.
     *
     *
     * @param offset   The offset to a {@linkplain ZipSearcher#CENTRAL_DIRECTORY_HEADER} instance.
     * @param raf      The {@linkplain RandomAccessFile} that is needed in order to read the file itself.
     * @param function A way to add "functionality" with a {@linkplain SearchData}, which will be {@linkplain SearchData.State#SKIPPING Skipping} or {@linkplain SearchData.State#SEARCHING Searching}
     * @return {@linkplain ZipEntry} or {@code null} if it cycles to the end.
     * @throws IOException {@linkplain RandomAccessFile#seek(long)}, {@linkplain RandomAccessFile#getFilePointer()} and {@linkplain RandomAccessFile#read(byte[])}
     * @throws CentralDirectoryException if Central Directory is invalid by the name length not equally the bytes read. Which could be corrupted or just invalid.
     */
    public static ZipEntry getEntry(long offset, RandomAccessFile raf, Function<SearchData, ZipEntry> function) throws IOException {
        raf.seek(offset);

        long currentOffset;

        while (true) {
            currentOffset = raf.getFilePointer();
            int read = raf.read(centralDirectoryBuffer);

            if (read != 46) {
                break; // so this might be the end of the file or a corruption
            }

            int signature = toInt4(centralDirectoryBuffer, 0);
            if (signature != CENTRAL_DIRECTORY_HEADER) {
                break;
            }

            short nameLength = toShort2(centralDirectoryBuffer, 28);
            byte[] nameBytes = new byte[nameLength];
            int nameRead = raf.read(nameBytes);
            if (nameRead != nameLength) {
                throw new CentralDirectoryException(String.format("Central Directory is invalid (offset: %d) cannot read name bytes", offset));
            }
            String name = new String(nameBytes);

            short extraFieldLength = toShort2(centralDirectoryBuffer, 30);
            short fileCommentLength = toShort2(centralDirectoryBuffer, 32);

            // Why is the skip amount `raf.getFilePointer() + (extraFieldLength + fileCommentLength)`
            // Well when you read an n of bytes it moves n bytes. Normally the math includes the nameLength
            // However, we are already reading the nameLength.

            // This is the "searching" state
            SearchData data = new SearchData(name, currentOffset, raf.getFilePointer() + (extraFieldLength + fileCommentLength), centralDirectoryBuffer);
            ZipEntry zip = function.apply(data);
            if (zip != null) {
                return zip;
            }

            // This is the "skipping" state
            long skipAmount = raf.getFilePointer() + (extraFieldLength + fileCommentLength);
            function.apply(new SearchData(name, currentOffset, skipAmount));
            raf.seek(skipAmount);
        }

        return null;
    }

    /**
     * A utility method to get a LocalFileEntry and parse it to a {@linkplain ZipEntry}.
     * <p>
     *     This method basically parses from a localFileHeaderOffset and returns what is parsed. It will return {@code null} if it doesn't have a {@linkplain ZipSearcher#LOCAL_FILE_HEADER}
     *     or doesn't read 30 bytes which is the size of the LocalFile data.
     *
     *
     * @param fileName              The name that represents this local file.
     * @param compressedSize        We don't parse the compressedSize as for some reason it's 0 in the LocalFile, so we parse it from the Central Directory.
     * @param uncompressedSize      We don't parse the uncompressedSize as for some reason it's 0 in the LocalFile, so we parse it from the Central Directory.
     * @param localFileHeaderOffset The offset that represents the start of the LocalFile.
     * @param raf                   {@linkplain RandomAccessFile} that we use to read from.
     * @return {@linkplain ZipEntry} if successful, or {@code null} if it can't read it or is not a LocalFile.
     * @throws IOException {@linkplain RandomAccessFile#seek(long)}, {@linkplain RandomAccessFile#getFilePointer()} and {@linkplain RandomAccessFile#read(byte[])}
     * @throws InvalidZipFileException If we can't read the local files compressed data.
     */
    public static ZipEntry getLocalFileAsZipEntry(String fileName, int compressedSize, int uncompressedSize, int localFileHeaderOffset, RandomAccessFile raf) throws IOException {
        raf.seek(localFileHeaderOffset);

        int bytesRead = raf.read(localFileBuffer);

        if (bytesRead != 30) {
            return null;
        }

        int signature = toInt4(localFileBuffer, 0);
        if (signature != LOCAL_FILE_HEADER) {
            return null;
        }

        short compressionMethod = toShort2(localFileBuffer, 8);

        short nameLength = toShort2(localFileBuffer, 26);
        short extraFieldLength = toShort2(localFileBuffer, 28);

        raf.seek((nameLength + extraFieldLength) + raf.getFilePointer());

        byte[] fileData = new byte[compressedSize];
        int fileRead = raf.read(fileData);

        if (fileRead != compressedSize) {
            throw new InvalidZipFileException(String.format("Local file: %s was unable to read the compressed data", fileName));
        }

        return new ZipEntry(fileName, fileData, compressedSize, uncompressedSize, compressionMethod);
    }

    /**
     * A utility method to find the End Of Directory so we can locate the start of the Central Directory.
     * <p>
     *     This method searches a chunk of 64KB from back to front. We move an imaginary pointer one byte at a time to hopefully find the {@linkplain ZipSearcher#END_OF_CENTRAL_DIRECTORY}
     *     If we can't find it we return -1, if we can we return the Central Directory Offset found.
     *
     * @param raf {@linkplain RandomAccessFile} to read the file.
     * @return {@code -1} if we can't locate the End of Central Directory, or the central directory offset.
     * @throws IOException {@linkplain RandomAccessFile#seek(long)}, {@linkplain RandomAccessFile#length()} and {@linkplain RandomAccessFile#read(byte[])}
     */
    public static int getFirstCentralDirectoryOffset(final RandomAccessFile raf) throws IOException {
        long fileLength = raf.length();

        if (fileLength < 4) {
            throw new InvalidZipFileException("Zip file is too small.");
        }

        long pointer = fileLength - 4;
        long startPointer = Math.max(fileLength - 65536, 0); // 64KB chunk

        while (pointer >= startPointer) {
            raf.seek(pointer);

            int bufferRead = raf.read(endOfCentralDirectorySigBuffer);

            if (bufferRead != 4) {
                pointer--;
                continue;
            }

            int signature = toInt4(endOfCentralDirectorySigBuffer, 0);
            if (signature == END_OF_CENTRAL_DIRECTORY) {

                // We only read 18 bytes here even though the CD is 22 on 32bit zips
                // This is because we already moved the pointer 4 bytes up when we checked the signature.
                byte[] buffer = new byte[18];
                raf.read(buffer);
                return toInt4(buffer, 12);
            }

            pointer--;
        }
        return -1;
    }
}