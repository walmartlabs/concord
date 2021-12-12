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

import ch.qos.logback.classic.ClassicConstants;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.oracle.truffle.js.runtime.util.DirectByteBufferHelper;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConcordLogLayout extends PatternLayout {

    public static boolean SEGMENTED = false;

    private static final Map<Long, Stats> statsHolder = new ConcurrentHashMap<>();

    @Override
    public String doLayout(ILoggingEvent event) {
        if (SEGMENTED) {
            return doSegmentedLayout(event);
        } else {
            return super.doLayout(event);
        }
    }

    private String doSegmentedLayout(ILoggingEvent event) {
        Long segmentId = LogUtils.getSegmentId();
        if (segmentId == null) {
            segmentId = 0L;
        }

        Stats stats = processStats(segmentId, event);

        boolean done = isDone(event);
        String msg = done ? "\n" : super.doLayout(event);

        // msgLength|segmentId|DONE?|warnings|errors|msg
        String header = msg.length() + "|" + segmentId + "|" + (done ? "1" : "0") + "|";
        if (stats != null) {
            header += stats.getWarnings() + "|" + stats.getErrors() + "|";
        }

        return header + msg;
    }

    private static boolean isDone(ILoggingEvent event) {
        return event.getMarker() != null && event.getMarker().contains(ClassicConstants.FINALIZE_SESSION_MARKER);
    }

    private Stats processStats(long segmentId, ILoggingEvent event) {
        Stats stats = null;
        if (event.getLevel() == Level.ERROR) {
            stats = statsHolder.computeIfAbsent(segmentId, s -> new Stats())
                    .incError();
        } else if (event.getLevel() == Level.WARN) {
            stats = statsHolder.computeIfAbsent(segmentId, s -> new Stats())
                    .incWarn();
        }

        if (isDone(event)) {
            stats = statsHolder.remove(segmentId);
        }

        return stats;
    }

    private static class Stats {

        private int errors = 0;
        private int warnings = 0;

        public int getErrors() {
            return errors;
        }

        public int getWarnings() {
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
