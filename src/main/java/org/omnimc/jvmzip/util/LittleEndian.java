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

/**
 * All multibyte values in a Zip needs to be converted to Little Endian in understand it.
 *
 * @author <a href=https://github.com/CadenCCC>Caden</a>
 * @since 1.0.0
 */
public class LittleEndian {

    /**
     * Converts a 4-byte little-endian byte array into an integer starting from a specified offset.
     *
     * @param bytes  a byte array of 4 bytes in little-endian order
     * @param offset the offset in the byte array from which to start the conversion
     * @return the integer value represented by the byte array in little-endian order
     */
    public static int toInt4(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF)) |
                ((bytes[offset + 1] & 0xFF) << 8) |
                ((bytes[offset + 2] & 0xFF) << 16) |
                ((bytes[offset + 3] & 0xFF) << 24);
    }


    /**
     * Converts a 2-byte little-endian byte array into a short starting from a specified offset.
     *
     * @param bytes  a byte array of 2 bytes in little-endian order
     * @param offset the offset in the byte array from which to start the conversion
     * @return the short value represented by the byte array in little-endian order
     */
    public static short toShort2(byte[] bytes, int offset) {
        return (short) (((bytes[offset] & 0xFF)) |
                ((bytes[offset + 1] & 0xFF) << 8));
    }

}