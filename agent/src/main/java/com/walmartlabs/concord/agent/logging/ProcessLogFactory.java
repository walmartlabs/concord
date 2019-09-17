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

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

public class ProcessLogFactory {

    private final Path logDir;
    private final long logStreamMaxDelay;
    private final LogAppender logAppender;

    public ProcessLogFactory(Path logDir, long logStreamMaxDelay, LogAppender logAppender) {
        this.logDir = logDir;
        this.logStreamMaxDelay = logStreamMaxDelay;
        this.logAppender = logAppender;
    }

    public RedirectedProcessLog createRedirectedLog(UUID instanceId) throws IOException {
        return new RedirectedProcessLog(logDir, instanceId, logAppender, logStreamMaxDelay);
    }

    public RemoteProcessLog createRemoteLog(UUID instanceId) {
        return new RemoteProcessLog(instanceId, logAppender);
    }
}
