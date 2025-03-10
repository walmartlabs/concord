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
import com.walmartlabs.concord.agent.logging.LogAppender;
import com.walmartlabs.concord.agent.logging.LogSegmentStats;
import com.walmartlabs.concord.agent.logging.SegmentedLogsConsumer;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentHeader;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentSerializer;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.UUID;

import static com.walmartlabs.concord.agent.logging.RedirectedProcessLog.Chunk;
import static org.mockito.Mockito.*;

public class SegmentedLogsConsumerTest {

    private LogAppender logAppender;
    private SegmentedLogsConsumer consumer;

    @BeforeEach
    public void init() {
        this.logAppender = mock(LogAppender.class);
        consumer = new SegmentedLogsConsumer(UUID.randomUUID(), logAppender);
    }

    @Test
    public void test1() {
        String msg = "hello";
        byte[] ab = bb(1, msg);
        consumer.accept(toChunk(ab));

        verify(logAppender, times(1)).appendLog(any(), eq(1L), eq(msg.getBytes()));
        verify(logAppender, times(1)).updateSegment(any(), eq(1L), eq(new LogSegmentStats(null, 2, 1)));
    }

    /**
     * in: |7|1|1|1|2|hello1\n|8|1|1|1|2|hello223
     */
    @Test
    public void test2() {
        String msg1 = "hello1\n";
        byte[] s1 = bb(1, msg1);

        String msg2 = "hello223";
        byte[] s2 = bb(1, msg2);

        consumer.accept(toChunk(Bytes.concat(s1, s2)));

        verify(logAppender, times(1)).appendLog(any(), eq(1L), eq(Bytes.concat(msg1.getBytes(), msg2.getBytes())));
        verify(logAppender, times(1)).updateSegment(any(), eq(1L), eq(new LogSegmentStats(null, 2, 1)));
    }

    /**
     * in: |7|1|1|1|2|hello1\n|8|2|1|1|2|hello223
     */
    @Test
    public void test3() {
        String msg1 = "hello1\n";
        byte[] s1 = bb(1, msg1);

        String msg2 = "hello223";
        byte[] s2 = bb(2, msg2);

        consumer.accept(toChunk(Bytes.concat(s1, s2)));

        verify(logAppender, times(1)).appendLog(any(), eq(1L), eq(msg1.getBytes()));
        verify(logAppender, times(1)).appendLog(any(), eq(2L), eq(msg2.getBytes()));
        verify(logAppender, times(1)).updateSegment(any(), eq(1L), eq(new LogSegmentStats(null, 2, 1)));
    }

    /**
     * in: |7|1|1|1|2|hello1\n|8|2|1|1|2|hello223
     */
    @Test
    public void test4() {
        String msg = "hello";
        byte[] s = bb(1, msg);

        consumer.accept(toChunk(Arrays.copyOfRange(s, 0, s.length - 3)));

        byte[] p1 = {'h', 'e'};
        verify(logAppender, times(1)).appendLog(any(), eq(1L), eq(p1));

        byte[] p2 = {'l', 'l', 'o'};
        consumer.accept(toChunk(p2));
        verify(logAppender, times(1)).appendLog(any(), eq(1L), eq(p2));
        verify(logAppender, times(2)).updateSegment(any(), eq(1L), eq(new LogSegmentStats(null, 2, 1)));
    }

    /**
     * in: |5|1|1|0|0|hello
     */
    @Test
    public void test5() {
        String msg = "hello";
        byte[] ab = bb(1, msg, 0, 0);

        consumer.accept(toChunk(ab));

        verify(logAppender, times(1)).appendLog(any(), eq(1L), eq(msg.getBytes()));
        verifyNoMoreInteractions(logAppender);
    }

    /**
     * in: |5|1|1|0|0|hellotrash|3|2|1|0|0|bye
     */
    @Test
    public void test6() {
        String msg1 = "hello";
        String msg2 = "bye";
        byte[] s1 = bb(1, msg1, 0, 0);
        byte[] s2 = bb(2, msg2, 0, 0);
        byte[] ab = Bytes.concat(s1, "trash".getBytes(), s2);

        System.out.println(">>>" + new String(ab));

        consumer.accept(toChunk(ab));

        verify(logAppender, times(1)).appendLog(any(), eq(1L), eq(msg1.getBytes()));
        verify(logAppender, times(1)).appendLog(any(), eq(0L), eq("trash".getBytes()));
        verify(logAppender, times(1)).appendLog(any(), eq(2L), eq(msg2.getBytes()));
        verifyNoMoreInteractions(logAppender);
    }

    private static Chunk toChunk(byte[] ab) {
        return new Chunk(ab, ab.length) {
        };
    }

    private static byte[] bb(int segmentId, String msg) {
        return bb(segmentId, msg, 2, 1);
    }

    private static byte[] bb(int segmentId, String msg, int errorCount, int warnCount) {
        byte[] ab = msg.getBytes();

        byte[] header = LogSegmentSerializer.serializeHeader(LogSegmentHeader.builder()
                .segmentId(segmentId)
                .length(ab.length)
                .warnCount(warnCount)
                .errorCount(errorCount)
                .status(LogSegmentStatus.RUNNING)
                .build());

        return Bytes.concat(header, ab);
    }
}
