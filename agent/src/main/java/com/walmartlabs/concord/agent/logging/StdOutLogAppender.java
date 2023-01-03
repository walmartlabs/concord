package com.walmartlabs.concord.agent.logging;

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

import java.util.UUID;

public class StdOutLogAppender implements LogAppender {

    private static final String PREFIX = "RUNNER: ";

    @Override
    public void appendLog(UUID instanceId, byte[] ab) {
        System.out.print(PREFIX + new String(ab));
    }

    @Override
    public boolean appendLog(UUID instanceId, long segmentId, byte[] ab) {
        System.out.print(PREFIX + new String(ab));
        return true;
    }

    @Override
    public boolean updateSegment(UUID instanceId, long segmentId, LogSegmentStats stats) {
        return true;
    }
}
