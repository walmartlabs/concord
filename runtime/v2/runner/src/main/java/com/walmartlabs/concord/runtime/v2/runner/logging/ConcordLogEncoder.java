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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConcordLogEncoder extends LayoutWrappingEncoder<ILoggingEvent> {

    public static boolean SEGMENTED = false;

    private static final byte[] EMPTY_AB = new byte[0];
    private static final Stats EMPTY_STATS = new Stats();
    private static final Map<Long, Stats> statsHolder = new ConcurrentHashMap<>();

    @Override
    public byte[] encode(ILoggingEvent event) {
        if (!SEGMENTED) {
            return super.encode(event);
        }

        SegmentMarker marker = segmentMarker(event);
        boolean done = marker != null && marker.getStatus() != null;
        byte[] msgBytes = EMPTY_AB;
        if (!done) {
            String msg = layout.doLayout(event);
            msgBytes = convertToBytes(msg);
        }
        byte[] header = header(event, marker, msgBytes);
        return concat(header, msgBytes);
    }

    private byte[] header(ILoggingEvent event, SegmentMarker marker, byte[] msgBytes) {
        Long segmentId;
        if (marker != null) {
            segmentId = marker.getSegmentId();
        } else {
            segmentId = LogUtils.getSegmentId();
        }

        if (segmentId == null) {
            segmentId = 0L;
        }

        Stats stats = processStats(segmentId, event, marker != null && marker.getStatus() != null);
        return String.format("|%d|%d|%d|%d|%d|",
                msgBytes.length, segmentId, status(marker), stats.warnings(), stats.errors()).getBytes();
    }

    public static SegmentMarker segmentMarker(ILoggingEvent event) {
        if (event.getMarkerList() == null) {
            return null;
        }

        return (SegmentMarker) event.getMarkerList().stream()
                .filter(m -> m instanceof SegmentMarker)
                .findFirst().orElse(null);
    }

    private static Stats processStats(long segmentId, ILoggingEvent event, boolean closeSegment) {
        Stats stats = EMPTY_STATS;
        if (event.getLevel() == Level.ERROR) {
            stats = statsHolder.computeIfAbsent(segmentId, s -> new Stats())
                    .incError();
        } else if (event.getLevel() == Level.WARN) {
            stats = statsHolder.computeIfAbsent(segmentId, s -> new Stats())
                    .incWarn();
        }

        if (closeSegment) {
            stats = statsHolder.remove(segmentId);
            if (stats == null) {
                stats = EMPTY_STATS;
            }
        }

        return stats;
    }

    private static int status(SegmentMarker marker) {
        if (marker == null || marker.getStatus() == null) {
            return 0;
        }

        return marker.getStatus().id();
    }

    private static byte[] convertToBytes(String s) {
        return s.getBytes();
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] c = new byte[a.length + b.length];
        System.arraycopy(a, 0, c, 0, a.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
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
