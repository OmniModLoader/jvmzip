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

package org.omnimc.jvmzip;

import org.omnimc.jvmzip.entry.ZipEntry;
import org.omnimc.jvmzip.throwables.CentralDirectoryException;
import org.omnimc.jvmzip.util.ZipSearcher;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;

import static org.omnimc.jvmzip.util.LittleEndian.toInt4;
import static org.omnimc.jvmzip.util.ZipSearcher.getFirstCentralDirectoryOffset;
import static org.omnimc.jvmzip.util.ZipSearcher.getLocalFileAsZipEntry;

/**
 * A simple to use and fast Zip Parser.
 * <p>
 *     This Zip parser only supports Zip32 and Deflate compression or no compression at all.
 * <p>
 *     The way this parser works is it trys to lazy parse, we do this by only parsing entries when {@linkplain ZipParser#getEntry(String, BiPredicate)} is called.
 *     We try to be smart in this approach by keeping a map of central directory offsets we skipped, and keeping the next offset which is where we left off.
 *
 * @author <a href=https://github.com/CadenCCC>Caden</a>
 * @since 1.0.0
 */
public final class ZipParser implements Closeable {

    private RandomAccessFile raf;
    private long centralDirectoryOffset;
    private long nextCentralDirectoryOffset = -1;

    private final Map<String, Long> centralDirectories = new HashMap<>();

    public ZipParser(URI fileUri) throws IOException {
        this(new File(fileUri));
    }

    public ZipParser(String filename) throws IOException {
        this(new RandomAccessFile(filename, "r"));
    }

    public ZipParser(File file) throws IOException {
        this(new RandomAccessFile(file, "r"));
    }

    public ZipParser(RandomAccessFile raf) throws IOException {
        this.raf = raf;
        this.centralDirectoryOffset = getFirstCentralDirectoryOffset(raf);
    }

    /**
     * A {@linkplain ZipParser#getEntry(String, BiPredicate)} but with the {@linkplain BiPredicate} as {@linkplain String#equals(Object)}
     * 
     * @param fileName The file you are wanting to search for.
     * @return {@linkplain ZipEntry}, or {@code null} if the entry does not exist.
     * @throws IOException {@linkplain ZipSearcher#getEntry(long, RandomAccessFile, Function)} is the culprit.
     * @throws CentralDirectoryException If the central directory was not initialized or if it doesn't exist.
     */
    public ZipEntry getEntry(String fileName) throws IOException {
        return getEntry(fileName, String::equals);
    }

    /**
     * A method that searches a Zip by looking with a filter.
     * <p>
     *     The way we do this is by using {@linkplain ZipSearcher#getEntry(long, RandomAccessFile, Function)},
     *     however we try to keep this optimized, we keep track of skipped entries with a cache.
     * <p>
     *     With this cache we check if the name and filter can be applied to any of the entries in it.
     *     If it can we set the {@linkplain ZipParser#nextCentralDirectoryOffset} to the current {@linkplain ZipParser#centralDirectoryOffset}.
     *     Then after we do that we set the {@linkplain ZipParser#centralDirectoryOffset} with the one we found in the cache.
     * <p>
     *     After searching we check if the {@linkplain ZipParser#nextCentralDirectoryOffset} is equal to -1 if not we revert our changes.
     *
     * @param fileName The file you're wanting to search for.
     * @param filter   The "rule" you want it to abide by, first String is your provided file name, and the next is the name of the entry it got.
     * @return {@linkplain ZipEntry}, or {@code null} if it couldn't find the entry you're looking for, or it couldn't pass the filter.
     * @throws IOException {@linkplain ZipSearcher#getEntry(long, RandomAccessFile, Function)} is the culprit.
     * @throws CentralDirectoryException If the central directory was not initialized or if it doesn't exist.
     */
    public ZipEntry getEntry(String fileName, BiPredicate<String, String> filter) throws IOException {
        if (centralDirectoryOffset == -1) {
            throw new CentralDirectoryException("Either the central directory has not been initialized, or it doesn't exist.");
        }

        long offset = getDirectoryOffset(fileName, filter);
        if (offset != -1) {
            nextCentralDirectoryOffset = centralDirectoryOffset;
            centralDirectoryOffset = offset;
        }

        ZipEntry entry = ZipSearcher.getEntry(centralDirectoryOffset, raf, (searchData -> {
            switch (searchData.getState()) {
                case SKIPPING:
                    updateCacheAndOffset(searchData.getName(), searchData.getCurrentOffset(), searchData.getSkipAmount());
                    return null;
                case SEARCHING:
                    if (!filter.test(fileName, searchData.getName())) {
                        return null;
                    }

                    int compressedSize = toInt4(searchData.getBuffer(), 20);
                    int uncompressedSize = toInt4(searchData.getBuffer(), 24);
                    int localFileHeaderOffset = toInt4(searchData.getBuffer(), 42);

                    try {
                        ZipEntry localFileAsZipEntry = getLocalFileAsZipEntry(searchData.getName(), compressedSize, uncompressedSize, localFileHeaderOffset, raf);
                        updateCacheAndOffset(searchData.getName(), searchData.getCurrentOffset(), searchData.getSkipAmount());
                        return localFileAsZipEntry;
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
            }
            return null;
        }));

        if (nextCentralDirectoryOffset != -1) {
            centralDirectoryOffset = nextCentralDirectoryOffset;
            nextCentralDirectoryOffset = -1;
        }

        return entry;
    }

    /**
     * Getting an offset from the cache.
     * <p>
     *     The reason we are doing it this way is that the Stream Api is a lot to load in.
     *     So we are doing it the "old" way for performance. (legit saves us like 2ms)
     *
     * @param name   The name you're wanting to search for.
     * @param filter The filter you are wanting to check by.
     * @return an offset or {@code -1} if no offset is found.
     */
    private long getDirectoryOffset(String name, BiPredicate<String, String> filter) {
        Long value = centralDirectories.get(name);
        if (value != null) {
            return value;
        }

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

    /**
     * Swaps the ZipParsers focus.
     * <p>
     *     This makes it so you can have one instance of ZipParser, and not need to create a new one everytime.
     *
     * @param uri An instance of {@linkplain URI} with the scheme being "file:"
     * @throws IOException {@linkplain RandomAccessFile#close()} and {@linkplain ZipSearcher#getFirstCentralDirectoryOffset(RandomAccessFile)}
     */
    public void swap(URI uri) throws IOException {
        swap(new File(uri));
    }

    /**
     * Swaps the ZipParsers focus.
     * <p>
     *     This makes it so you can have one instance of ZipParser, and not need to create a new one everytime.
     *
     * @param file An instance of {@linkplain File}.
     * @throws IOException {@linkplain RandomAccessFile#close()} and {@linkplain ZipSearcher#getFirstCentralDirectoryOffset(RandomAccessFile)}
     */
    public void swap(File file) throws IOException {
        swap(new RandomAccessFile(file, "r"));
    }

    /**
     * Swaps the ZipParsers focus.
     * <p>
     *     This makes it so you can have one instance of ZipParser, and not need to create a new one everytime.
     *
     * @param fileName A valid file path.
     * @throws IOException {@linkplain RandomAccessFile#close()} and {@linkplain ZipSearcher#getFirstCentralDirectoryOffset(RandomAccessFile)}
     */
    public void swap(String fileName) throws IOException {
        swap(new RandomAccessFile(fileName, "r"));
    }

    /**
     * Swaps the ZipParsers focus.
     * <p>
     *     This makes it so you can have one instance of ZipParser, and not need to create a new one everytime.
     *
     * @param randomAccessFile {@linkplain RandomAccessFile} that we are going to search.
     * @throws IOException {@linkplain RandomAccessFile#close()} and {@linkplain ZipSearcher#getFirstCentralDirectoryOffset(RandomAccessFile)}
     */
    public void swap(RandomAccessFile randomAccessFile) throws IOException {
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