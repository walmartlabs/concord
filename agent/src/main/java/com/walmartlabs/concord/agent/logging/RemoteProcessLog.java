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

import java.io.InputStream;
import java.util.UUID;

/**
 * Simple log implementation that sends all data into a {@link LogAppender} directly.
 */
public class RemoteProcessLog extends AbstractProcessLog {

    private final UUID instanceId;
    private final LogAppender appender;

    public RemoteProcessLog(UUID instanceId, LogAppender appender) {
        this.instanceId = instanceId;
        this.appender = appender;
    }

    @Override
    public void log(InputStream src) {
        throw new IllegalStateException("Not supported");
    }

    @Override
    protected void log(String message) {
        appender.appendLog(instanceId, message.getBytes());
    }
}
