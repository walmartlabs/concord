package com.walmartlabs.concord.agent.logging;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

/**
 * Process log implementation for agent-generated messages.
 */
public class RemoteProcessLog extends SessionProcessLog {

    public RemoteProcessLog(UUID instanceId, LogAppender appender) {
        this(instanceId, appender, false);
    }

    public RemoteProcessLog(UUID instanceId, LogAppender appender, boolean segmented) {
        super(new DefaultProcessLogSession(createOutput(instanceId, appender, segmented)), 0L);
    }

    private static ProcessOutputDecoder createOutput(UUID instanceId, LogAppender appender, boolean segmented) {
        var transport = new LogAppenderProcessLogTransport(instanceId, appender);
        if (segmented) {
            return new SegmentedOutputDecoder(transport);
        }
        return new PlainOutputDecoder(transport);
    }
}
