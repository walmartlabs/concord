package com.walmartlabs.concord.server.process.logs;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.codahale.metrics.Counter;
import com.walmartlabs.concord.common.LogUtils;
import com.walmartlabs.concord.server.Listeners;
import com.walmartlabs.concord.server.process.LogSegment;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.Range;
import com.walmartlabs.concord.server.sdk.log.ProcessLogEntry;
import com.walmartlabs.concord.server.sdk.metrics.InjectCounter;
import org.jooq.DSLContext;

import javax.inject.Inject;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.common.LogUtils.LogLevel;
import static com.walmartlabs.concord.server.process.logs.ProcessLogsDao.ProcessLog;

public class ProcessLogManager {

    private static final long SYSTEM_SEGMENT_ID = 0;
    private static final String SYSTEM_SEGMENT_NAME = "system";
    private final ProcessLogsDao logsDao;
    private final Listeners listeners;

    @InjectCounter
    private final Counter logBytesAppended;

    @Inject
    public ProcessLogManager(ProcessLogsDao logsDao,
                             Listeners listeners,
                             Counter logBytesAppended) {

        this.logsDao = logsDao;
        this.listeners = listeners;
        this.logBytesAppended = logBytesAppended;
    }

    public void info(ProcessKey processKey, String log, Object... args) {
        log(processKey, LogLevel.INFO, log, args);
    }

    public void warn(ProcessKey processKey, String log, Object... args) {
        log(processKey, LogLevel.WARN, log, args);
    }

    public void error(ProcessKey processKey, String log, Object... args) {
        log(processKey, LogLevel.ERROR, log, args);
    }

    public void log(ProcessKey processKey, String msg) {
        log(processKey, msg.getBytes());
    }

    public int log(ProcessKey processKey, byte[] msg) {
        return log(processKey, SYSTEM_SEGMENT_ID, msg);
    }

    public List<LogSegment> listSegments(ProcessKey processKey, int limit, int offset, int attemptNumber) {
        return logsDao.listSegments(processKey, limit, offset, attemptNumber);
    }

    public void createSystemSegment(DSLContext tx, ProcessKey processKey, int attemptNumber) {
        logsDao.createSegment(tx, SYSTEM_SEGMENT_ID, processKey, null, SYSTEM_SEGMENT_NAME, attemptNumber, null);
    }

    public long createSegment(ProcessKey processKey, UUID correlationId, String name, OffsetDateTime createdAt, int attemptNumber) {
        if (SYSTEM_SEGMENT_NAME.equals(name)) {
            return SYSTEM_SEGMENT_ID;
        }
        return logsDao.createSegment(processKey, correlationId, name, createdAt, attemptNumber, LogSegment.Status.RUNNING.name());
    }

    public void updateSegment(ProcessKey processKey, long segmentId, LogSegment.Status status, Integer warnings, Integer errors) {
        logsDao.updateSegment(processKey, segmentId, status, warnings, errors);
    }

    public ProcessLog segmentData(ProcessKey processKey, long segmentId, Integer start, Integer end) {
        return logsDao.segmentData(processKey, segmentId, start, end);
    }

    public ProcessLog get(ProcessKey processKey, Integer start, Integer end) {
        return logsDao.data(processKey, start, end);
    }

    public int log(ProcessKey processKey, long segmentId, byte[] msg) {
        Range range = logsDao.append(processKey, segmentId, msg);
        logBytesAppended.inc(msg.length);

        ProcessLogEntry entry = ProcessLogEntry.builder()
                .processKey(processKey)
                .range(range)
                .msg(msg)
                .build();

        listeners.onProcessLogAppend(entry);

        return range.upper();
    }

    private void log(ProcessKey processKey, LogLevel level, String msg, Object... args) {
        log(processKey, LogUtils.formatMessage(level, msg, args));
    }
}
