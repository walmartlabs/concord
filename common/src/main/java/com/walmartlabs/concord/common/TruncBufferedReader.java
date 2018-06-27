package com.walmartlabs.concord.common;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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


import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

public class TruncBufferedReader extends BufferedReader {

    public static final int DEFAULT_MAX_LINE_LENGTH = 4*1024;
    private static final int CR = '\r';
    private static final int LF = '\n';

    private final int readerMaxLineLen;
    private final char[] data;

    public TruncBufferedReader(Reader reader) {
        this(reader, DEFAULT_MAX_LINE_LENGTH);
    }

    public TruncBufferedReader(Reader reader, int maxLineLen) {
        super(reader);
        if (maxLineLen <= 0) {
            throw new IllegalArgumentException("maxLineLen must be greater than 0");
        }

        this.readerMaxLineLen = maxLineLen;
        this.data = new char[readerMaxLineLen];
    }

    @Override
    public String readLine() throws IOException {
        int currentPos = 0;
        int currentCharVal = super.read();

        while ((currentCharVal != CR) && (currentCharVal != LF) && (currentCharVal >= 0)) {
            data[currentPos++] = (char) currentCharVal;
            if (currentPos < readerMaxLineLen) {
                currentCharVal = super.read();
            } else {
                break;
            }
        }

        if (currentCharVal < 0) {
            if (currentPos > 0) {
                return (new String(data, 0, currentPos));
            } else {
                return null;
            }
        } else {
            int skipped = skipTillEndOfLine(currentCharVal);
            String result = new String(data, 0, currentPos);
            if (skipped > 0) {
                result += "...[skipped " + skipped + " bytes]";
            }
            return result;
        }
    }

    private int skipTillEndOfLine(int currentCharVal) throws IOException {
        int skippedCount = 0;
        while ((currentCharVal != CR) && (currentCharVal != LF) && (currentCharVal >= 0)) {
            currentCharVal = super.read();
            skippedCount++;
        }

        if (currentCharVal < 0) {
            return skippedCount - 1;
        }

        if (currentCharVal == CR) {
            super.mark(1);
            if (super.read() != LF) {
                super.reset();
            }
        }

        return skippedCount - 1;
    }
}
