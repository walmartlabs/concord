package com.walmartlabs.concord.agent.logging;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2021 Walmart Inc.
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

import javax.inject.Inject;
import java.util.Set;
import java.util.UUID;

public class CombinedLogAppender implements LogAppender {

    private final Set<LogAppender> appenders;

    @Inject
    public CombinedLogAppender(Set<LogAppender> appenders) {
        this.appenders = appenders;
    }

    @Override
    public void appendLog(UUID instanceId, byte[] ab) {
        appenders.forEach(a -> a.appendLog(instanceId, ab));
    }

    @Override
    public boolean appendLog(UUID instanceId, long segmentId, byte[] ab) {
        boolean result = true;
        for (LogAppender a : appenders) {
            boolean done = a.appendLog(instanceId, segmentId, ab);
            result = result && done;
        }
        return result;
    }

    @Override
    public boolean updateSegment(UUID instanceId, long segmentId, LogSegmentStats stats) {
        boolean result = true;
        for (LogAppender a : appenders) {
            boolean done = a.updateSegment(instanceId, segmentId, stats);
            result = result && done;
        }
        return result;
    }
}
