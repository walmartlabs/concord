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

import com.walmartlabs.concord.common.LogUtils;

import java.io.IOException;

public class DefaultProcessLogSession implements ProcessLogSession {

    private final ProcessOutputDecoder output;

    public DefaultProcessLogSession(ProcessOutputDecoder output) {
        this.output = output;
    }

    @Override
    public ProcessOutputSink output() {
        return output;
    }

    @Override
    public void info(String message, Object... args) {
        writeMessage(LogUtils.formatMessage(LogUtils.LogLevel.INFO, message, args));
    }

    @Override
    public void warn(String message, Object... args) {
        writeMessage(LogUtils.formatMessage(LogUtils.LogLevel.WARN, message, args));
    }

    @Override
    public void error(String message, Object... args) {
        writeMessage(LogUtils.formatMessage(LogUtils.LogLevel.ERROR, message, args));
    }

    @Override
    public void close() throws IOException {
        output.close();
    }

    private void writeMessage(String message) {
        try {
            output.write(message.getBytes());
            output.flush();
        } catch (IOException e) {
            throw new RuntimeException("Error writing process log message", e);
        }
    }
}
