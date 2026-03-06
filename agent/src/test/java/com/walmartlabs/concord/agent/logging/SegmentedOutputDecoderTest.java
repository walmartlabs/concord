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

import com.google.common.primitives.Bytes;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentHeader;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentSerializer;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentStatus;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SegmentedOutputDecoderTest {

    @Test
    public void flushesSegmentOutputAndCounters() throws Exception {
        var transport = new RecordingProcessLogTransport();
        var decoder = new SegmentedOutputDecoder(transport);

        decoder.write(segmentBytes(7, "hello", 2, 1, LogSegmentStatus.RUNNING));
        decoder.flush();

        assertEquals("hello", transport.segmentLog(7));
        assertEquals(1, transport.updates().size());
        assertEquals(new RecordingProcessLogTransport.SegmentUpdate(7, new LogSegmentStats(null, 2, 1)),
                transport.updates().get(0));
    }

    @Test
    public void preservesPartialPayloadsAcrossChunkBoundaries() throws Exception {
        var transport = new RecordingProcessLogTransport();
        var decoder = new SegmentedOutputDecoder(transport);

        var header = segmentHeader(1, 5, 0, 0, LogSegmentStatus.RUNNING);
        var payload = "hello".getBytes(StandardCharsets.UTF_8);

        decoder.write(copyOfRange(header, 0, 4));
        decoder.flush();
        assertEquals("", transport.segmentLog(1));

        decoder.write(Bytes.concat(copyOfRange(header, 4, header.length), copyOfRange(payload, 0, 3)));
        decoder.flush();
        assertEquals("hel", transport.segmentLog(1));

        decoder.write(copyOfRange(payload, 3, payload.length));
        decoder.flush();
        assertEquals("hello", transport.segmentLog(1));
    }

    @Test
    public void preservesPartialHeadersAcrossChunkBoundaries() throws Exception {
        var transport = new RecordingProcessLogTransport();
        var decoder = new SegmentedOutputDecoder(transport);

        var header = segmentHeader(1, 5, 2, 1, LogSegmentStatus.RUNNING);
        var payload = "hello".getBytes(StandardCharsets.UTF_8);

        decoder.write(copyOfRange(header, 0, header.length - 2));
        decoder.flush();
        assertEquals("", transport.segmentLog(1));
        assertEquals(0, transport.updates().size());

        decoder.write(Bytes.concat(copyOfRange(header, header.length - 2, header.length), payload));
        decoder.flush();

        assertEquals("hello", transport.segmentLog(1));
        assertEquals(1, transport.updates().size());
        assertEquals(new RecordingProcessLogTransport.SegmentUpdate(1, new LogSegmentStats(null, 2, 1)),
                transport.updates().get(0));
    }

    @Test
    public void coalescesMultipleSegmentsForTheSameIdInOneWrite() throws Exception {
        var transport = new RecordingProcessLogTransport();
        var decoder = new SegmentedOutputDecoder(transport);

        decoder.write(Bytes.concat(
                segmentBytes(1, "hello1\n", 2, 1, LogSegmentStatus.RUNNING),
                segmentBytes(1, "hello223", 2, 1, LogSegmentStatus.RUNNING)));
        decoder.flush();

        assertEquals("hello1\nhello223", transport.segmentLog(1));
        assertEquals(1, transport.updates().size());
        assertEquals(new RecordingProcessLogTransport.SegmentUpdate(1, new LogSegmentStats(null, 2, 1)),
                transport.updates().get(0));
    }

    @Test
    public void flushesDifferentSegmentIdsSeparately() throws Exception {
        var transport = new RecordingProcessLogTransport();
        var decoder = new SegmentedOutputDecoder(transport);

        decoder.write(Bytes.concat(
                segmentBytes(1, "hello1\n", 0, 0, LogSegmentStatus.RUNNING),
                segmentBytes(2, "hello223", 0, 0, LogSegmentStatus.RUNNING)));
        decoder.flush();

        assertEquals("hello1\n", transport.segmentLog(1));
        assertEquals("hello223", transport.segmentLog(2));
        assertEquals(0, transport.updates().size());
    }

    @Test
    public void mapsInvalidBytesToSystemSegmentZero() throws Exception {
        var transport = new RecordingProcessLogTransport();
        var decoder = new SegmentedOutputDecoder(transport);

        decoder.write(segmentBytes(1, "hello", 0, 0, LogSegmentStatus.RUNNING));
        decoder.write("trash".getBytes(StandardCharsets.UTF_8));
        decoder.write(segmentBytes(2, "bye", 0, 0, LogSegmentStatus.RUNNING));
        decoder.flush();

        assertEquals("hello", transport.segmentLog(1));
        assertEquals("trash", transport.segmentLog(0));
        assertEquals("bye", transport.segmentLog(2));
    }

    @Test
    public void preservesFinalSegmentStatusAcrossChunkMerges() throws Exception {
        var transport = new RecordingProcessLogTransport();
        var decoder = new SegmentedOutputDecoder(transport);

        decoder.write(Bytes.concat(
                segmentBytes(9, "hello", 2, 1, LogSegmentStatus.RUNNING),
                segmentBytes(9, "", 0, 0, LogSegmentStatus.OK)));
        decoder.flush();

        assertEquals("hello", transport.segmentLog(9));
        assertEquals(1, transport.updates().size());
        assertEquals(new RecordingProcessLogTransport.SegmentUpdate(9, new LogSegmentStats(LogSegmentStatus.OK, 2, 1)),
                transport.updates().get(0));
    }

    private static byte[] segmentBytes(long segmentId, String message, int errorCount, int warnCount, LogSegmentStatus status) {
        var payload = message.getBytes(StandardCharsets.UTF_8);
        return Bytes.concat(segmentHeader(segmentId, payload.length, errorCount, warnCount, status), payload);
    }

    private static byte[] segmentHeader(long segmentId, int length, int errorCount, int warnCount, LogSegmentStatus status) {
        return LogSegmentSerializer.serializeHeader(LogSegmentHeader.builder()
                .segmentId(segmentId)
                .length(length)
                .errorCount(errorCount)
                .warnCount(warnCount)
                .status(status)
                .build());
    }

    private static byte[] copyOfRange(byte[] bytes, int from, int to) {
        var len = to - from;
        var result = new byte[len];
        System.arraycopy(bytes, from, result, 0, len);
        return result;
    }
}
