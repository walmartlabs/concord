package com.walmartlabs.concord.runtime.v25.runner;

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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentHeader;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentSerializer;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentStatus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Emits the framing consumed by the agent's process-log collector. */
public final class V25LogEncoder extends LayoutWrappingEncoder<ILoggingEvent> {

    private static volatile boolean segmented;
    private static final ThreadLocal<Long> segment = new ThreadLocal<>();
    private static final Map<Long, Stats> stats = new ConcurrentHashMap<>();

    static void segmented(boolean value) {
        segmented = value;
        if (!value) {
            segment.remove();
            stats.clear();
        }
    }

    static void segment(long id) {
        segment.set(id);
    }

    static void clearSegment() {
        segment.remove();
    }

    static SegmentScope scope(Long id) {
        var previous = segment.get();
        if (id == null) {
            segment.remove();
        } else {
            segment.set(id);
        }
        return new SegmentScope(previous);
    }

    @Override
    public byte[] encode(ILoggingEvent event) {
        if (!segmented) {
            return super.encode(event);
        }
        var segmentId = segmentId();
        var segmentStats = stats.computeIfAbsent(segmentId, ignored -> new Stats());
        if (event.getLevel() == Level.WARN) {
            segmentStats.warnings.incrementAndGet();
        } else if (event.getLevel() == Level.ERROR) {
            segmentStats.errors.incrementAndGet();
        }
        var header = LogSegmentHeader.builder().length(0).segmentId(segmentId)
                .warnCount(segmentStats.warnings.get()).errorCount(segmentStats.errors.get())
                .status(LogSegmentStatus.RUNNING).build();
        return LogSegmentSerializer.serialize(header, layout.doLayout(event));
    }

    private static long segmentId() {
        var value = segment.get();
        return value != null ? value : 0L;
    }

    static void finish(long id, LogSegmentStatus status) {
        var value = status(id, status);
        System.out.write(value, 0, value.length);
        System.out.flush();
    }

    static byte[] status(long id, LogSegmentStatus status) {
        var segmentStats = stats.remove(id);
        var warnings = segmentStats != null ? segmentStats.warnings.get() : 0;
        var errors = segmentStats != null ? segmentStats.errors.get() : 0;
        var header = LogSegmentHeader.builder().length(0).segmentId(id)
                .warnCount(warnings).errorCount(errors).status(status).build();
        return LogSegmentSerializer.serialize(header, null);
    }

    private static final class Stats {
        private final AtomicInteger warnings = new AtomicInteger();
        private final AtomicInteger errors = new AtomicInteger();
    }

    static final class SegmentScope implements AutoCloseable {

        private final Long previous;

        private SegmentScope(Long previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (previous == null) {
                segment.remove();
            } else {
                segment.set(previous);
            }
        }
    }
}
