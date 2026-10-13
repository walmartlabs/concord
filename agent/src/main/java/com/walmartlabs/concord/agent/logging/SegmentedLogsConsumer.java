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

import com.walmartlabs.concord.runtime.common.logger.ImmutableLogSegmentHeader;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentHeader;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentSerializer;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentStatus;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Consumer;

import static com.walmartlabs.concord.agent.logging.SegmentHeaderParser.Position;
import static com.walmartlabs.concord.agent.logging.SegmentHeaderParser.Segment;

public class SegmentedLogsConsumer implements Consumer<RedirectedProcessLog.Chunk> {

    private static final byte[] EMPTY = new byte[0];

    /**
     * Max length of a status time (epoch millis). A longer body is not a time
     * (e.g. a corrupted header in the raw output) and is processed as a log.
     */
    private static final int MAX_STATUS_TIME_LENGTH = 20;

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

        // the body of a status marker is the time of the status, it can't be processed in parts
        Segment partialSegment = findPartialSegment(segments, ab.length);
        byte[] partialStatus = null;
        if (partialSegment != null && hasStatusTime(partialSegment)) {
            segments.remove(partialSegment);
            partialStatus = concat(LogSegmentSerializer.serializeHeader(partialSegment.header()),
                    Arrays.copyOfRange(ab, partialSegment.msgStart(), ab.length));
            partialSegment = null;
        }

        Map<Long, List<Segment>> segmentsById = byId(segments);
        for (Map.Entry<Long, List<Segment>> e : segmentsById.entrySet()) {
            List<Segment> logs = e.getValue().stream()
                    .filter(s -> !hasStatusTime(s))
                    .toList();

            int buffLength = logs.stream().mapToInt(h -> actualLength(h, ab.length)).sum();
            byte[] segmentBuffer = new byte[buffLength];
            fillBuffer(logs, ab, segmentBuffer);

            if (segmentBuffer.length > 0) {
                // TODO: retry?
                logAppender.appendLog(instanceId, e.getKey(), segmentBuffer);
            }

            LogSegmentStats stats = findStats(e.getValue(), ab);
            if (stats != null) {
                logAppender.updateSegment(instanceId, e.getKey(), stats);
            }
        }

        if (partialSegment != null) {
            // the status is already reported, the rest is a log
            LogSegmentHeader header = ImmutableLogSegmentHeader.copyOf(partialSegment.header())
                    .withStatus(LogSegmentStatus.RUNNING);
            unparsed = LogSegmentSerializer.serializeHeader(
                    header, header.length() - actualLength(partialSegment, ab.length));
        } else if (partialStatus != null) {
            unparsed = partialStatus;
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

    /**
     * A status marker: its body is not a part of the log.
     */
    private static boolean isStatus(Segment segment) {
        return segment.header().status() != LogSegmentStatus.RUNNING;
    }

    /**
     * A status marker with the time in its body.
     */
    private static boolean hasStatusTime(Segment segment) {
        return isStatus(segment) && segment.header().length() <= MAX_STATUS_TIME_LENGTH;
    }

    /**
     * The time of the status from the marker's body (epoch millis), {@code null} for markers
     * without the time (older runners).
     */
    private static OffsetDateTime statusTime(Segment segment, byte[] ab) {
        int length = segment.header().length();
        if (length == 0 || !hasStatusTime(segment)) {
            return null;
        }

        try {
            long millis = Long.parseLong(new String(ab, segment.msgStart(), length, StandardCharsets.US_ASCII));
            return OffsetDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
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

    private static LogSegmentStats findStats(List<Segment> segments, byte[] ab) {
        boolean done = false;
        LogSegmentStatus status = null;
        OffsetDateTime statusUpdatedAt = null;
        int errorCount = 0;
        int warnCount = 0;
        ListIterator<Segment> it = segments.listIterator(segments.size());
        while (it.hasPrevious()) {
            Segment segment = it.previous();
            if (isStatus(segment)) {
                done = true;
                status = segment.header().status();
                statusUpdatedAt = statusTime(segment, ab);
            }
            if (warnCount == 0 && segment.header().warnCount() > 0) {
                warnCount = segment.header().warnCount();
            }
            if (errorCount == 0 && segment.header().errorCount() > 0) {
                errorCount = segment.header().errorCount();
            }
        }

        if (done || errorCount > 0 || warnCount > 0) {
            return new LogSegmentStats(status, errorCount, warnCount, statusUpdatedAt);
        }
        return null;
    }
}
