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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ModeAwareProcessLog implements ProcessLog, ProcessLogModeConfigurator {

    private final UUID instanceId;
    private final LogAppender appender;

    private final List<byte[]> bufferedMessages = new ArrayList<>();

    private ProcessLog delegate;

    public ModeAwareProcessLog(UUID instanceId, LogAppender appender, long flushIntervalMillis) {
        this.instanceId = instanceId;
        this.appender = appender;
    }

    @Override
    public synchronized void setSegmented(boolean segmented) {
        if (delegate != null) {
            return;
        }

        delegate = new RemoteProcessLog(instanceId, appender, segmented);
        flushBufferedMessages();
    }

    @Override
    public synchronized void delete() {
        ensureDelegate(false).delete();
    }

    @Override
    public synchronized void log(InputStream src) throws IOException {
        ensureDelegate(false).log(src);
    }

    @Override
    public void info(String log, Object... args) {
        writeMessage(LogUtils.formatMessage(LogUtils.LogLevel.INFO, log, args), false);
    }

    @Override
    public void warn(String log, Object... args) {
        writeMessage(LogUtils.formatMessage(LogUtils.LogLevel.WARN, log, args), false);
    }

    @Override
    public void error(String log, Object... args) {
        writeMessage(LogUtils.formatMessage(LogUtils.LogLevel.ERROR, log, args), true);
    }

    private synchronized void writeMessage(String message, boolean fallbackToPlain) {
        var bytes = message.getBytes();
        if (delegate == null && !fallbackToPlain) {
            bufferedMessages.add(bytes);
            return;
        }

        ensureDelegate(false);
        logBytes(delegate, bytes);
    }

    private void flushBufferedMessages() {
        for (var message : bufferedMessages) {
            logBytes(delegate, message);
        }
        bufferedMessages.clear();
    }

    private ProcessLog ensureDelegate(boolean segmented) {
        if (delegate == null) {
            delegate = new RemoteProcessLog(instanceId, appender, segmented);
            flushBufferedMessages();
        }
        return delegate;
    }

    private static void logBytes(ProcessLog log, byte[] bytes) {
        try {
            log.log(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new RuntimeException("Error writing process log bytes", e);
        }
    }
}
