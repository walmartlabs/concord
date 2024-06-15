package com.walmartlabs.concord.client2.impl;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

public class ByteArrayBuffer {

    private byte[] array;
    private int len;

    public ByteArrayBuffer(int capacity) {
        super();
        this.array = new byte[capacity];
    }

    public void append(byte[] b) {
        append(b, 0, b.length);
    }

    public void append(byte[] b, int off, int len) {
        if (b == null) {
            return;
        }
        if ((off < 0) || (off > b.length) || (len < 0) ||
                ((off + len) < 0) || ((off + len) > b.length)) {
            throw new IndexOutOfBoundsException("off: "+off+" len: "+len+" b.length: "+b.length);
        }
        if (len == 0) {
            return;
        }
        int newlen = this.len + len;
        if (newlen > this.array.length) {
            expand(newlen);
        }
        System.arraycopy(b, off, this.array, this.len, len);
        this.len = newlen;
    }

    private void expand(int newlen) {
        byte[] newArray = new byte[Math.max(this.array.length << 1, newlen)];
        System.arraycopy(this.array, 0, newArray, 0, this.len);
        this.array = newArray;
    }

    public byte[] array() {
        return this.array;
    }

    public byte[] toByteArray() {
        final byte[] b = new byte[this.len];
        if (this.len > 0) {
            System.arraycopy(this.array, 0, b, 0, this.len);
        }
        return b;
    }

    public int length() {
        return this.len;
    }

    public void clear() {
        this.len = 0;
    }
}
