package com.walmartlabs.concord.runtime.v2.runner.logging;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import com.walmartlabs.concord.runtime.common.logger.LogSegmentStatus;
import org.slf4j.Marker;

import java.util.Iterator;

public class SegmentStatusMarker implements Marker {

    private final long segmentId;
    private final LogSegmentStatus status;

    public SegmentStatusMarker(long segmentId, LogSegmentStatus status) {
        this.segmentId = segmentId;
        this.status = status;
    }

    public long getSegmentId() {
        return segmentId;
    }

    public LogSegmentStatus getStatus() {
        return status;
    }

    @Override
    public String getName() {
        return "close-segment-marker";
    }

    @Override
    public void add(Marker reference) {
    }

    @Override
    public boolean remove(Marker reference) {
        return false;
    }

    @Override
    public boolean hasChildren() {
        return false;
    }

    @Override
    public boolean hasReferences() {
        return false;
    }

    @Override
    public Iterator<Marker> iterator() {
        return null;
    }

    @Override
    public boolean contains(Marker other) {
        return false;
    }

    @Override
    public boolean contains(String name) {
        return false;
    }
}