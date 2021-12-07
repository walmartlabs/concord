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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;

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

        Consumer<RedirectedProcessLog.Chunk> logConsumer;
//        if (segmented) {
//            logConsumer = new SegmentedLogsConsumer(instanceId, logAppender);
//        } else {
            logConsumer = chunk -> {
                byte[] ab = new byte[chunk.len()];
                System.arraycopy(chunk.bytes(), 0, ab, 0, chunk.len());
                logAppender.appendLog(instanceId, ab);
            };
//        }
        return new RedirectedProcessLog(dst, logStreamMaxDelay, logConsumer);
    }

    public RemoteProcessLog createRemoteLog(UUID instanceId) {
        return new RemoteProcessLog(instanceId, logAppender);
    }
}
