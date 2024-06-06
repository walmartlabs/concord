package com.walmartlabs.concord.runtime.v2.runner.logging;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2021 Walmart Inc.
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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentHeader;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentSerializer;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentStatus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConcordLogEncoder extends LayoutWrappingEncoder<ILoggingEvent> {

    public static boolean SEGMENTED = false;

    private static final Stats EMPTY_STATS = new Stats();
    private static final Map<Long, Stats> statsHolder = new ConcurrentHashMap<>();

    @Override
    public byte[] encode(ILoggingEvent event) {
        if (!SEGMENTED) {
            return super.encode(event);
        }

        SegmentStatusMarker marker = segmentMarker(event);
        boolean isStatusEvent = marker != null;
        String msg = null;
        if (!isStatusEvent) {
            msg = layout.doLayout(event);
        }
        return LogSegmentSerializer.serialize(header(event, marker), msg);
    }

    public static SegmentStatusMarker segmentMarker(ILoggingEvent event) {
        if (event.getMarkerList() == null) {
            return null;
        }

        return (SegmentStatusMarker) event.getMarkerList().stream()
                .filter(m -> m instanceof SegmentStatusMarker)
                .findFirst()
                .orElse(null);
    }

    private LogSegmentHeader header(ILoggingEvent event, SegmentStatusMarker marker) {
        Long segmentId;
        if (marker != null) {
            segmentId = marker.getSegmentId();
        } else {
            segmentId = LogUtils.getSegmentId();
        }

        if (segmentId == null) {
            segmentId = 0L;
        }

        Stats stats = processStats(segmentId, event, isFinalStatus(marker));

        return LogSegmentHeader.builder()
                .length(0)
                .segmentId(segmentId)
                .warnCount(stats.warnings())
                .errorCount(stats.errors())
                .status(status(marker))
                .build();
    }

    private static boolean isFinalStatus(SegmentStatusMarker marker) {
        return marker != null && marker.getStatus() != LogSegmentStatus.RUNNING;
    }

    private static Stats processStats(long segmentId, ILoggingEvent event, boolean finalStatus) {
        Stats stats = EMPTY_STATS;
        if (event.getLevel() == Level.ERROR) {
            stats = statsHolder.computeIfAbsent(segmentId, s -> new Stats())
                    .incError();
        } else if (event.getLevel() == Level.WARN) {
            stats = statsHolder.computeIfAbsent(segmentId, s -> new Stats())
                    .incWarn();
        }

        if (finalStatus) {
            stats = statsHolder.remove(segmentId);
            if (stats == null) {
                stats = EMPTY_STATS;
            }
        }

        return stats;
    }

    private static LogSegmentStatus status(SegmentStatusMarker marker) {
        if (marker == null || marker.getStatus() == null) {
            return LogSegmentStatus.RUNNING;
        }

        return marker.getStatus();
    }

    private static class Stats {

        private int errors = 0;
        private int warnings = 0;

        public int errors() {
            return errors;
        }

        public int warnings() {
            return warnings;
        }

        @JsonIgnore
        public Stats incError() {
            synchronized (this) {
                errors++;
                return this;
            }
        }

        @JsonIgnore
        public Stats incWarn() {
            synchronized (this) {
                warnings++;
                return this;
            }
        }
    }
}
