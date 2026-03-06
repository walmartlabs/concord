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

import java.io.IOException;
import java.io.InputStream;

public class SessionProcessLog implements ProcessLog {

    private final ProcessLogSession session;
    private final ProcessOutputPump outputPump;

    public SessionProcessLog(ProcessLogSession session, long flushIntervalMillis) {
        this.session = session;
        this.outputPump = new ProcessOutputPump(flushIntervalMillis);
    }

    @Override
    public void delete() {
        try {
            session.close();
        } catch (IOException e) {
            throw new RuntimeException("Error closing the process log session", e);
        }
    }

    @Override
    public void log(InputStream src) throws IOException {
        outputPump.pump(src, session.output());
    }

    @Override
    public void info(String log, Object... args) {
        session.info(log, args);
    }

    @Override
    public void warn(String log, Object... args) {
        session.warn(log, args);
    }

    @Override
    public void error(String log, Object... args) {
        session.error(log, args);
    }
}
