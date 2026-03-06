package com.walmartlabs.concord.agent.logging;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import java.io.PrintStream;

public class StdoutMirrorTransport implements ProcessLogTransport {

    private static final String PREFIX = "RUNNER: ";

    private final PrintStream out;

    public StdoutMirrorTransport() {
        this(System.out);
    }

    StdoutMirrorTransport(PrintStream out) {
        this.out = out;
    }

    @Override
    public DeliveryStatus appendSystem(byte[] bytes) {
        out.print(PREFIX + new String(bytes));
        return DeliveryStatus.DELIVERED;
    }

    @Override
    public DeliveryStatus appendSegment(long segmentId, byte[] bytes) {
        out.print(PREFIX + new String(bytes));
        return DeliveryStatus.DELIVERED;
    }

    @Override
    public DeliveryStatus updateSegment(long segmentId, LogSegmentStats stats) {
        return DeliveryStatus.DELIVERED;
    }
}
