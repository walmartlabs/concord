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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RecordingProcessLogTransport implements ProcessLogTransport {

    private final ByteArrayOutputStream systemLog = new ByteArrayOutputStream();
    private final Map<Long, ByteArrayOutputStream> segments = new LinkedHashMap<>();
    private final List<SegmentUpdate> updates = new ArrayList<>();

    @Override
    public synchronized DeliveryStatus appendSystem(byte[] bytes) {
        systemLog.write(bytes, 0, bytes.length);
        return DeliveryStatus.DELIVERED;
    }

    @Override
    public synchronized DeliveryStatus appendSegment(long segmentId, byte[] bytes) {
        segments.computeIfAbsent(segmentId, id -> new ByteArrayOutputStream())
                .write(bytes, 0, bytes.length);
        return DeliveryStatus.DELIVERED;
    }

    @Override
    public synchronized DeliveryStatus updateSegment(long segmentId, LogSegmentStats stats) {
        updates.add(new SegmentUpdate(segmentId, stats));
        return DeliveryStatus.DELIVERED;
    }

    public synchronized String systemLog() {
        return systemLog.toString(StandardCharsets.UTF_8);
    }

    public synchronized String segmentLog(long segmentId) {
        var out = segments.get(segmentId);
        if (out == null) {
            return "";
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    public synchronized List<SegmentUpdate> updates() {
        return List.copyOf(updates);
    }

    public record SegmentUpdate(long segmentId, LogSegmentStats stats) {
    }
}
