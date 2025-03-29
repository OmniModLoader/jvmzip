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

import java.io.RandomAccessFile;
import java.util.function.Function;

/**
 * Only used for {@linkplain ZipSearcher#getEntry(long, RandomAccessFile, Function)}, to provide details of what's happening.
 * 
 * @author <a href=https://github.com/CadenCCC>Caden</a>
 * @since 1.0.0
 */
public class SearchData {

    private final String name;
    private final long currentOffset;
    private final long skipAmount;

    private final byte[] buffer;
    private final State state;

    /**
     * This is the Skipping version of the constructor.
     *
     * @param name          The name of the current Central Directory it's skipping.
     * @param currentOffset The current offset it's skipping.
     * @param skipAmount    The skip amount to the next Central Directory.
     */
    public SearchData(String name, long currentOffset, long skipAmount) {
        this(name, currentOffset, skipAmount, null, State.SKIPPING);
    }

    /**
     * This is the Searching version of the constructor.
     *
     * @param name          The name of the current Central Directory it's searching.
     * @param currentOffset The current offset its searching.
     * @param skipAmount    The skip amount to the next Central Directory.
     * @param buffer        The buffer of the Central Directory it's searching.
     */
    public SearchData(String name, long currentOffset, long skipAmount, byte[] buffer) {
        this(name, currentOffset, skipAmount, buffer, State.SEARCHING);
    }

    private SearchData(String name, long currentOffset, long skipAmount, byte[] buffer, State state) {
        this.name = name;
        this.currentOffset = currentOffset;
        this.skipAmount = skipAmount;
        this.buffer = buffer;
        this.state = state;
    }

    public String getName() {
        return name;
    }

    public long getCurrentOffset() {
        return currentOffset;
    }

    public long getSkipAmount() {
        return skipAmount;
    }

    public byte[] getBuffer() {
        return buffer;
    }

    public State getState() {
        return state;
    }

    public enum State {
        /**
         * Skipping means it didn't return a {@linkplain ZipEntry}
         */
        SKIPPING,
        /**
         * Searching means you can investigate the current Central Directory and return a {@linkplain ZipEntry}
         */
        SEARCHING
    }
}