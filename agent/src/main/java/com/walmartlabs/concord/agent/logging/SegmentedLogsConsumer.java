package com.walmartlabs.concord.agent.logging;

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

import com.walmartlabs.concord.runtime.common.logger.LogSegmentHeader;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentSerializer;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentStatus;

import java.util.*;
import java.util.function.Consumer;

import static com.walmartlabs.concord.agent.logging.SegmentHeaderParser.Position;
import static com.walmartlabs.concord.agent.logging.SegmentHeaderParser.Segment;

public class SegmentedLogsConsumer implements Consumer<RedirectedProcessLog.Chunk> {

    private static final byte[] EMPTY = new byte[0];

    private final UUID instanceId;
    private final LogAppender logAppender;

    private byte[] unparsed = EMPTY;

    public SegmentedLogsConsumer(UUID instanceId, LogAppender logAppender) {
        this.instanceId = instanceId;
        this.logAppender = logAppender;
    }

    @Override
    public void accept(RedirectedProcessLog.Chunk chunk) {
        byte[] ab = new byte[unparsed.length + chunk.len()];
        if (unparsed.length > 0) {
            System.arraycopy(unparsed, 0, ab, 0, unparsed.length);
        }
        System.arraycopy(chunk.bytes(), 0, ab, unparsed.length, chunk.len());
        unparsed = EMPTY;

        List<Segment> segments = new ArrayList<>();
        List<Position> invalidSegments = new ArrayList<>();
        int pos = SegmentHeaderParser.parse(ab, segments, invalidSegments);

        invalidSegmentsToSystemSegments(invalidSegments, segments);
        Map<Long, List<Segment>> segmentsById = byId(segments);
        for (Map.Entry<Long, List<Segment>> e : segmentsById.entrySet()) {
            int buffLength = e.getValue().stream().mapToInt(h -> actualLength(h, ab.length)).sum();
            byte[] segmentBuffer = new byte[buffLength];
            fillBuffer(e.getValue(), ab, segmentBuffer);

            if (segmentBuffer.length > 0) {
                // TODO: retry?
                logAppender.appendLog(instanceId, e.getKey(), segmentBuffer);
            }

            LogSegmentStats stats = findStats(e.getValue());
            if (stats != null) {
                logAppender.updateSegment(instanceId, e.getKey(), stats);
            }
        }

        Segment partialSegment = findPartialSegment(segments, ab.length);
        if (partialSegment != null) {
            unparsed = LogSegmentSerializer.serializeHeader(
                    partialSegment.header(), partialSegment.header().length() - actualLength(partialSegment, ab.length));
        }

        if (pos < ab.length) {
            if (unparsed != EMPTY) {
                throw new RuntimeException("Unexpected partial segment and unparsed tail");
            }

            unparsed = Arrays.copyOfRange(ab, pos, ab.length);
        }
    }

    private void invalidSegmentsToSystemSegments(List<Position> invalidSegments, List<Segment> segments) {
        for (Position s : invalidSegments) {
            LogSegmentHeader header = LogSegmentHeader.builder()
                    .status(LogSegmentStatus.RUNNING)
                    .segmentId(0)
                    .errorCount(0)
                    .warnCount(0)
                    .length(s.end() - s.start())
                    .build();
            segments.add(new Segment(header, s.start()));
        }
    }

    private static int actualLength(Segment segment, int chunkLength) {
        return Math.min(chunkLength - segment.msgStart(), segment.header().length());
    }

    private static Map<Long, List<Segment>> byId(List<Segment> segments) {
        Map<Long, List<Segment>> result = new LinkedHashMap<>();
        for (Segment s : segments) {
            result.computeIfAbsent(s.header().segmentId(), id -> new ArrayList<>())
                    .add(s);
        }
        return result;
    }

    private static void fillBuffer(List<Segment> segments, byte[] from, byte[] to) {
        int i = 0;
        for (Segment s : segments) {
            int actualLength = actualLength(s, from.length);
            for (int j = 0; j < actualLength; j++) {
                to[i++] = from[j + s.msgStart()];
            }
        }
    }

    private static Segment findPartialSegment(List<Segment> segments, int chunkLength) {
        Segment result = null;
        for (Segment segment : segments) {
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
        boolean done = false;
        LogSegmentStatus status = null;
        int errorCount = 0;
        int warnCount = 0;
        ListIterator<Segment> it = segments.listIterator(segments.size());
        while (it.hasPrevious()) {
            Segment segment = it.previous();
            if (segment.header().status() != LogSegmentStatus.RUNNING) {
                done = true;
                status = segment.header().status();
            }
            if (warnCount == 0 && segment.header().warnCount() > 0) {
                warnCount = segment.header().warnCount();
            }
            if (errorCount == 0 && segment.header().errorCount() > 0) {
                errorCount = segment.header().errorCount();
            }
        }

        if (done || errorCount > 0 || warnCount > 0) {
            return new LogSegmentStats(status, errorCount, warnCount);
        }
        return null;
    }
}
