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

package org.omnimc.test.optimization;

import org.omnimc.jvmzip.entry.ZipEntry;
import org.omnimc.jvmzip.throwables.CentralDirectoryException;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import static org.omnimc.jvmzip.util.LittleEndian.toInt4;
import static org.omnimc.jvmzip.util.LittleEndian.toShort2;
import static org.omnimc.jvmzip.util.ZipSearcher.getFirstCentralDirectoryOffset;
import static org.omnimc.jvmzip.util.ZipSearcher.getLocalFileAsZipEntry;

/**
 * @author <a href=https://github.com/CadenCCC>Caden</a>
 * @since 1.0.0
 */
public final class FastZipParser implements Closeable { // TODO try to improve the system.

    private static final int CENTRAL_DIRECTORY_HEADER = 0x02014b50;

    private final Map<String, Long> centralDirectories = new HashMap<>();

    private RandomAccessFile raf;
    private long centralDirectoryOffset;
    private long nextCentralDirectoryOffset = -1;

    public FastZipParser(URI fileUri) throws IOException {
        this(new File(fileUri));
    }

    public FastZipParser(String filename) throws IOException {
        this(new RandomAccessFile(filename, "r"));
    }

    public FastZipParser(File file) throws IOException {
        this(new RandomAccessFile(file, "r"));
    }

    public FastZipParser(RandomAccessFile raf) throws IOException {
        this.raf = raf;
        this.centralDirectoryOffset = getFirstCentralDirectoryOffset(raf);
    }

    // TODO work on these so the performance isn't so ass
    public List<ZipEntry> getEntries() throws IOException {
        return getEntries((_) -> true);
    }

    public List<ZipEntry> getEntries(Predicate<String> filter) throws IOException {
        List<ZipEntry> entries = new ArrayList<>();

        while (true) {
            ZipEntry entry = getEntry("", (_, fileName) -> filter.test(fileName));
            if (entry == null) {
                return entries;
            }
        }
    }

    public ZipEntry getEntry(String fileName) throws IOException {
        return getEntry(fileName, String::equals);
    }

    @SuppressWarnings("DuplicatedCode")
    public ZipEntry getEntry(String fileName, BiPredicate<String, String> filter) throws IOException {
        if (centralDirectoryOffset == -1) {
            throw new CentralDirectoryException("Either the central directory has not been initialized, or it doesn't exist.");
        }

        long offset = getDirectoryOffset(fileName, filter);
        if (offset != -1) {
            nextCentralDirectoryOffset = centralDirectoryOffset;
            centralDirectoryOffset = offset;
        }

        raf.seek(centralDirectoryOffset);

        long currentOffset;

        byte[] buffer = new byte[46];
        while (true) { // We want to get the current offset now because if we get it after read it'll mess up
            currentOffset = raf.getFilePointer();
            int read = raf.read(buffer);

            if (read != 46) { // End of file
                break;
            }

            int signature = toInt4(buffer, 0);
            if (signature != CENTRAL_DIRECTORY_HEADER) {
                break;
            }

            short nameLength = toShort2(buffer, 28);
            byte[] nameBuffer = new byte[nameLength];
            int nameRead = raf.read(nameBuffer);
            if (nameRead != nameLength) {
                break;
            }
            String name = new String(nameBuffer);

            short extraFieldLength = toShort2(buffer, 30);
            short fileCommentLength = toShort2(buffer, 32);

            if (filter.test(fileName, name)) {
                int compressedSize = toInt4(buffer, 20);
                int uncompressedSize = toInt4(buffer, 24);
                int localFileHeaderOffset = toInt4(buffer, 42);

                long skipAmount = raf.getFilePointer() + (extraFieldLength + fileCommentLength);
                ZipEntry zipEntry = getLocalFileAsZipEntry(fileName, compressedSize, uncompressedSize, localFileHeaderOffset, raf);
                updateCacheAndOffset(name, currentOffset, skipAmount);
                return zipEntry;
            }

            long skipAmount = raf.getFilePointer() + (extraFieldLength + fileCommentLength);
            updateCacheAndOffset(name, currentOffset, skipAmount);
            raf.seek(skipAmount);
        }

        if (nextCentralDirectoryOffset != -1) {
            centralDirectoryOffset = nextCentralDirectoryOffset;
            nextCentralDirectoryOffset = -1;
        }
        return null;
    }

    private long getDirectoryOffset(String name, BiPredicate<String, String> filter) {
        for (Map.Entry<String, Long> entry : centralDirectories.entrySet()) {
            if (filter.test(name, entry.getKey())) {
                return entry.getValue();
            }
        }
        return -1L;
    }

    private void updateCacheAndOffset(String fileName, long currentOffset, long skipAmount) {
        if (nextCentralDirectoryOffset == -1) {
            centralDirectories.put(fileName, currentOffset);
            centralDirectoryOffset = skipAmount;
        }
    }

    public void swap(URI uri) throws IOException {
        swap(new File(uri));
    }

    public void swap(File file) throws IOException {
        swap(new RandomAccessFile(file, "r"));
    }

    public void swap(String fileName) throws IOException {
        swap(new RandomAccessFile(fileName, "r"));
    }

    private void swap(RandomAccessFile randomAccessFile) throws IOException {
        raf.close();
        centralDirectories.clear();
        nextCentralDirectoryOffset = -1;
        this.raf = randomAccessFile;
        centralDirectoryOffset = getFirstCentralDirectoryOffset(randomAccessFile);
    }

    @Override
    public void close() throws IOException {
        raf.close();
        centralDirectories.clear();
    }
}