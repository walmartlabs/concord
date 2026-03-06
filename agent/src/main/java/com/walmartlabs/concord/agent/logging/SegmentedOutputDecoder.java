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

import com.walmartlabs.concord.runtime.common.logger.LogSegmentHeader;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentSerializer;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentStatus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import static com.walmartlabs.concord.agent.logging.SegmentHeaderParser.Position;
import static com.walmartlabs.concord.agent.logging.SegmentHeaderParser.Segment;

public class SegmentedOutputDecoder implements ProcessOutputDecoder {

    private static final byte[] EMPTY = new byte[0];

    private final ProcessLogTransport transport;

    private byte[] unparsed = EMPTY;
    private final Map<Long, ByteArrayOutputStream> pendingSegments = new LinkedHashMap<>();
    private final Map<Long, LogSegmentStats> pendingStats = new LinkedHashMap<>();

    public SegmentedOutputDecoder(ProcessLogTransport transport) {
        this.transport = transport;
    }

    @Override
    public synchronized void write(byte[] bytes, int offset, int len) {
        var buffer = new byte[unparsed.length + len];
        if (unparsed.length > 0) {
            System.arraycopy(unparsed, 0, buffer, 0, unparsed.length);
        }
        System.arraycopy(bytes, offset, buffer, unparsed.length, len);
        unparsed = EMPTY;

        var segments = new ArrayList<Segment>();
        var invalidSegments = new ArrayList<Position>();
        var pos = SegmentHeaderParser.parse(buffer, segments, invalidSegments);

        invalidSegmentsToSystemSegments(invalidSegments, segments);
        var segmentsById = byId(segments);
        for (var entry : segmentsById.entrySet()) {
            var segmentBuffer = new byte[entry.getValue().stream().mapToInt(h -> actualLength(h, buffer.length)).sum()];
            fillBuffer(entry.getValue(), buffer, segmentBuffer);

            if (segmentBuffer.length > 0) {
                pendingSegments.computeIfAbsent(entry.getKey(), id -> new ByteArrayOutputStream())
                        .write(segmentBuffer, 0, segmentBuffer.length);
            }

            var stats = findStats(entry.getValue());
            if (stats != null) {
                pendingStats.merge(entry.getKey(), stats, SegmentedOutputDecoder::mergeStats);
            }
        }

        var partialSegment = findPartialSegment(segments, buffer.length);
        if (partialSegment != null) {
            unparsed = LogSegmentSerializer.serializeHeader(
                    partialSegment.header(),
                    partialSegment.header().length() - actualLength(partialSegment, buffer.length));
        }

        if (pos < buffer.length) {
            if (unparsed != EMPTY) {
                throw new RuntimeException("Unexpected partial segment and unparsed tail");
            }

            unparsed = Arrays.copyOfRange(buffer, pos, buffer.length);
        }
    }

    @Override
    public synchronized void flush() {
        for (var entry : pendingSegments.entrySet()) {
            transport.appendSegment(entry.getKey(), entry.getValue().toByteArray());
        }
        pendingSegments.clear();

        for (var entry : pendingStats.entrySet()) {
            transport.updateSegment(entry.getKey(), entry.getValue());
        }
        pendingStats.clear();
    }

    @Override
    public void close() throws IOException {
        flush();
    }

    private static void invalidSegmentsToSystemSegments(List<Position> invalidSegments, List<Segment> segments) {
        for (var invalidSegment : invalidSegments) {
            var header = LogSegmentHeader.builder()
                    .status(LogSegmentStatus.RUNNING)
                    .segmentId(0)
                    .errorCount(0)
                    .warnCount(0)
                    .length(invalidSegment.end() - invalidSegment.start())
                    .build();
            segments.add(new Segment(header, invalidSegment.start()));
        }
    }

    private static int actualLength(Segment segment, int chunkLength) {
        return Math.min(chunkLength - segment.msgStart(), segment.header().length());
    }

    private static Map<Long, List<Segment>> byId(List<Segment> segments) {
        var result = new LinkedHashMap<Long, List<Segment>>();
        for (var segment : segments) {
            result.computeIfAbsent(segment.header().segmentId(), id -> new ArrayList<>())
                    .add(segment);
        }
        return result;
    }

    private static void fillBuffer(List<Segment> segments, byte[] from, byte[] to) {
        var offset = 0;
        for (var segment : segments) {
            var actualLength = actualLength(segment, from.length);
            for (var i = 0; i < actualLength; i++) {
                to[offset++] = from[i + segment.msgStart()];
            }
        }
    }

    private static Segment findPartialSegment(List<Segment> segments, int chunkLength) {
        Segment result = null;
        for (var segment : segments) {
            if (actualLength(segment, chunkLength) != segment.header().length()) {
                if (result != null) {
                    throw new RuntimeException("Unexpected second partial segment");
                }
                result = segment;
            }
        }
        return result;
    }

    private static LogSegmentStats findStats(List<Segment> segments) {
        var done = false;
        LogSegmentStatus status = null;
        Integer errorCount = null;
        Integer warnCount = null;

        var iterator = segments.listIterator(segments.size());
        while (iterator.hasPrevious()) {
            var segment = iterator.previous();
            if (segment.header().status() != LogSegmentStatus.RUNNING) {
                done = true;
                status = segment.header().status();
            }
            if (warnCount == null && segment.header().warnCount() > 0) {
                warnCount = segment.header().warnCount();
            }
            if (errorCount == null && segment.header().errorCount() > 0) {
                errorCount = segment.header().errorCount();
            }
        }

        if (done || errorCount != null || warnCount != null) {
            return new LogSegmentStats(status, errorCount, warnCount);
        }
        return null;
    }

    private static LogSegmentStats mergeStats(LogSegmentStats current, LogSegmentStats next) {
        var status = next.status() != null ? next.status() : current.status();
        var errors = next.errors() != null ? next.errors() : current.errors();
        var warnings = next.warnings() != null ? next.warnings() : current.warnings();
        return new LogSegmentStats(status, errors, warnings);
    }
}
