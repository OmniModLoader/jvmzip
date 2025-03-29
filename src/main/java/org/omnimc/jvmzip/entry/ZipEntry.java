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

package org.omnimc.jvmzip.entry;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * @author <a href=https://github.com/CadenCCC>Caden</a>
 * @since 1.0.0
 */
public final class ZipEntry {

    private final String name;
    private final byte[] compressedData;

    private final int compressedSize;
    private final int uncompressedSize;
    private final int compressionMethod;

    private byte[] uncompressedData;

    private boolean hasUncompressedData = false;

    public ZipEntry(String name, byte[] compressedData, int compressedSize, int uncompressedSize, int compressionMethod) {
        this.compressedData = compressedData;
        this.compressedSize = compressedSize;
        this.uncompressedSize = uncompressedSize;
        this.compressionMethod = compressionMethod;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public byte[] getCompressedData() {
        return compressedData;
    }

    public int getCompressedSize() {
        return compressedSize;
    }

    public int getUncompressedSize() {
        return uncompressedSize;
    }

    public int getCompressionMethod() {
        return compressionMethod;
    }

    /**
     * This decompresses the raw bytes if you need it or not.
     *
     * @return A byte array that represents the raw data out of the Zip Entry.
     */
    public byte[] getRawData() {
        if (!hasUncompressedData || compressionMethod == 8) {
            this.decompress();
        }
        return uncompressedData;
    }

    /**
     * This decompresses (if needed) the raw bytes in a {@linkplain ByteArrayInputStream}.
     *
     * @return An instance of {@linkplain ByteArrayInputStream} with decompressed data inside it.
     */
    public InputStream getStreamData() {
        if (!hasUncompressedData || compressionMethod == 8) {
            this.decompress();
        }
        return new ByteArrayInputStream(uncompressedData);
    }

    public void decompress() {
        byte[] buffer = new byte[uncompressedSize];

        ByteArrayOutputStream bais = new ByteArrayOutputStream(uncompressedSize);

        Inflater inflater = new Inflater(true);
        try {
            inflater.setInput(compressedData);

            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                bais.write(buffer, 0, count);

                if (count == 0) {
                    if (inflater.needsInput()) {
                        break;
                    }
                }
            }
        } catch (DataFormatException e) {
            throw new RuntimeException(String.format("Decompressing file: %s", getName()), e);
        } finally {
            inflater.end();
        }

        uncompressedData = buffer;
        hasUncompressedData = true;
    }

    @Override
    public String toString() {
        return "ZipEntry{" +
                "name='" + name + '\'' +
                ", compressedData=" + Arrays.toString(compressedData) +
                ", compressedSize=" + compressedSize +
                ", uncompressedSize=" + uncompressedSize +
                ", compressionMethod=" + compressionMethod +
                ", uncompressedData=" + Arrays.toString(uncompressedData) +
                '}';
    }
}