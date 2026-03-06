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

import com.walmartlabs.concord.agent.cfg.AgentConfiguration;

import javax.inject.Inject;
import java.util.UUID;

public class ProcessLogFactory {

    private final long flushIntervalMillis;
    private final LogAppender logAppender;

    @Inject
    public ProcessLogFactory(AgentConfiguration cfg, LogAppender logAppender) {
        this.flushIntervalMillis = cfg.getLogMaxDelay();
        this.logAppender = logAppender;
    }

    public ProcessLog createRunnerLog(UUID instanceId, boolean segmented) {
        return new SessionProcessLog(new DefaultProcessLogSession(createOutput(instanceId, segmented)), flushIntervalMillis);
    }

    private ProcessOutputDecoder createOutput(UUID instanceId, boolean segmented) {
        var transport = new LogAppenderProcessLogTransport(instanceId, logAppender);
        if (segmented) {
            return new SegmentedOutputDecoder(transport);
        }
        return new PlainOutputDecoder(transport);
    }
}
