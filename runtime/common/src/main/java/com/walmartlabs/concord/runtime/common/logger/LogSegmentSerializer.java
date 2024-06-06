package com.walmartlabs.concord.runtime.common.logger;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

public final class LogSegmentSerializer {

    private static final byte[] EMPTY_AB = new byte[0];

    public static byte[] serialize(LogSegmentHeader header, String message) {
        byte[] msgBytes = EMPTY_AB;
        if (message != null) {
            msgBytes = message.getBytes();
        }
        byte[] headerBytes = serializeHeader(header, msgBytes.length);
        return concat(headerBytes, msgBytes);
    }

    public static byte[] serializeHeader(LogSegmentHeader header) {
        return serializeHeader(header, header.length());
    }

    public static byte[] serializeHeader(LogSegmentHeader header, int messageLength) {
        return String.format("|%d|%d|%d|%d|%d|",
                        messageLength,
                        header.segmentId(),
                        header.status().id(),
                        header.warnCount(),
                        header.errorCount())
                .getBytes();
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] c = new byte[a.length + b.length];
        System.arraycopy(a, 0, c, 0, a.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }

    private LogSegmentSerializer() {
    }
}
