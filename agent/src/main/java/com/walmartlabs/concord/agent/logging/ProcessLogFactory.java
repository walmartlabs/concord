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
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Named
@Singleton
public class ProcessLogFactory {

    private final Path logDir;
    private final long logStreamMaxDelay;
    private final LogAppender logAppender;

    @Inject
    public ProcessLogFactory(AgentConfiguration cfg, LogAppender logAppender) {
        this.logDir = cfg.getLogDir();
        this.logStreamMaxDelay = cfg.getLogMaxDelay();
        this.logAppender = logAppender;
    }

    public RedirectedProcessLog createRedirectedLog(UUID instanceId, boolean segmented) throws IOException {
        Path dst = logDir.resolve(instanceId.toString());
        if (Files.notExists(dst)) {
            Files.createDirectories(dst);
        }

        if (segmented) {
            return new SegmentedProcessLog(dst, instanceId, logAppender, logStreamMaxDelay);
        } else {
            return new RedirectedProcessLog(dst, instanceId, logAppender, logStreamMaxDelay);
        }
    }

    public RemoteProcessLog createRemoteLog(UUID instanceId) {
        return new RemoteProcessLog(instanceId, logAppender);
    }
}
