package com.walmartlabs.concord.agent.executors.runner;

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

import com.google.common.primitives.Bytes;
import com.walmartlabs.concord.agent.logging.SegmentHeaderParser;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentHeader;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentSerializer;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentStatus;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.walmartlabs.concord.agent.logging.SegmentHeaderParser.Position;
import static com.walmartlabs.concord.agent.logging.SegmentHeaderParser.Segment;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SegmentHeaderParserTest {

    /**
     * in: |5|2|1|1|2|hello
     */
    @Test
    public void test1() {
        String log = "hello";
        byte[] ab = bb(1, log);

        List<Segment> segments = new ArrayList<>();
        List<Position> invalidSegments = new ArrayList<>();
        int result = SegmentHeaderParser.parse(ab, segments, invalidSegments);
        assertEquals(ab.length, result);
        assertEquals(1, segments.size());
        assertEquals(log, msg(ab, segments.get(0)));
        assertEquals(0, invalidSegments.size());
    }

    /**
     * in: |7|1|1|1|2|hello-1|8|2|1|1|2|hello-21
     */
    @Test
    public void test1_1() {
        byte[] ab = Bytes.concat(bb(1, "hello-1"), bb(2, "hello-21"));

        List<Segment> segments = new ArrayList<>();
        List<Position> invalidSegments = new ArrayList<>();
        int result = SegmentHeaderParser.parse(ab, segments, invalidSegments);
        assertEquals(ab.length, result);
        assertEquals(2, segments.size());
        assertEquals("hello-1", msg(ab, segments.get(0)));
        assertEquals("hello-21", msg(ab, segments.get(1)));
        assertEquals(0, invalidSegments.size());
    }

    /**
     * in: hello
     */
    @Test
    public void test2() {
        String log = "hello";
        byte[] ab = log.getBytes();

        List<Segment> segments = new ArrayList<>();
        List<SegmentHeaderParser.Position> invalidSegments = new ArrayList<>();
        int result = SegmentHeaderParser.parse(ab, segments, invalidSegments);
        assertEquals(ab.length, result);
        assertEquals(0, segments.size());
        assertEquals(1, invalidSegments.size());
        Position i = invalidSegments.get(0);
        assertEquals(log, new String(Arrays.copyOfRange(ab, i.start(), i.end())));
    }

    /**
     * in: 123|5|2|1|1|2|hello
     */
    @Test
    public void test3() {
        String log = "hello";
        byte[] ab = {'1', '2', '3'};
        ab = Bytes.concat(ab, bb(2, log));

        List<Segment> segments = new ArrayList<>();
        List<Position> invalidSegments = new ArrayList<>();
        int result = SegmentHeaderParser.parse(ab, segments, invalidSegments);

        assertEquals(ab.length, result);
        assertEquals(1, segments.size());
        assertEquals("hello", msg(ab, segments.get(0)));

        assertEquals(1, invalidSegments.size());
        Position i = invalidSegments.get(0);
        assertEquals("123", new String(Arrays.copyOfRange(ab, i.start(), i.end())));
    }

    /**
     * in: |5|2|1|1|2|hello123
     */
    @Test
    public void test4() {
        String log = "hello";
        byte[] ab = {'1', '2', '3'};
        ab = Bytes.concat(bb(2, log), ab);

        List<Segment> segments = new ArrayList<>();
        List<Position> invalidSegments = new ArrayList<>();
        int result = SegmentHeaderParser.parse(ab, segments, invalidSegments);

        assertEquals(ab.length, result);
        assertEquals(1, segments.size());

        assertEquals(1, invalidSegments.size());
        Position i = invalidSegments.get(0);
        assertEquals("123", new String(Arrays.copyOfRange(ab, i.start(), i.end())));
    }

    /**
     * in: |5|2|1
     */
    @Test
    public void test5() {
        byte[] ab = {'|', '5', '|', '2', '|', '1'};

        List<Segment> segments = new ArrayList<>();
        List<Position> invalidSegments = new ArrayList<>();
        int result = SegmentHeaderParser.parse(ab, segments, invalidSegments);

        assertEquals(0, result);
        assertEquals(0, segments.size());
        assertEquals(0, invalidSegments.size());
    }

    /**
     * in: abc|5|2|1
     */
    @Test
    public void test6() {
        byte[] ab = {'a', 'b', 'c', '|', '5', '|', '2', '|', '1'};

        List<Segment> segments = new ArrayList<>();
        List<Position> invalidSegments = new ArrayList<>();
        int result = SegmentHeaderParser.parse(ab, segments, invalidSegments);

        assertEquals(3, result);
        assertEquals(0, segments.size());
        assertEquals(1, invalidSegments.size());
        Position i = invalidSegments.get(0);
        assertEquals("abc", new String(Arrays.copyOfRange(ab, i.start(), i.end())));
    }

    /**
     * in: |5|2|1|1|2|he
     */
    @Test
    public void test7() {
        String log = "hello";
        byte[] full = bb(1, log);
        byte[] ab = Arrays.copyOfRange(full, 0, full.length - 3);

        List<Segment> segments = new ArrayList<>();
        List<Position> invalidSegments = new ArrayList<>();
        int result = SegmentHeaderParser.parse(ab, segments, invalidSegments);

        assertEquals(ab.length, result);
        assertEquals(1, segments.size());
        assertEquals("he", msg(ab, segments.get(0)));
        assertEquals(0, invalidSegments.size());
    }

    /**
     *
     * |0|552|1|0|0|
     */
    @Test
    public void testParseSegmentEndMarker() {
        String log = "|0|552|1|0|0|";
        byte[] ab = log.getBytes(StandardCharsets.UTF_8);

        List<Segment> segments = new ArrayList<>();
        List<Position> invalidSegments = new ArrayList<>();

        int result = SegmentHeaderParser.parse(ab, segments, invalidSegments);

        assertEquals(ab.length, result);
        assertEquals(1, segments.size());
        Segment s = segments.get(0);
        assertEquals(0, s.header().length());
        assertEquals(LogSegmentStatus.OK, s.header().status());
    }

    private static String msg(byte[] ab, Segment segment) {
        int to = Math.min(ab.length, segment.msgStart() + segment.header().length());
        return new String(Arrays.copyOfRange(ab, segment.msgStart(), to));
    }

    private static byte[] bb(int segmentId, String msg) {
        byte[] ab = msg.getBytes();

        byte[] header = LogSegmentSerializer.serializeHeader(LogSegmentHeader.builder()
                .segmentId(segmentId)
                .length(ab.length)
                .warnCount(1)
                .errorCount(2)
                .status(LogSegmentStatus.RUNNING)
                .build());

        return Bytes.concat(header, ab);
    }
}
