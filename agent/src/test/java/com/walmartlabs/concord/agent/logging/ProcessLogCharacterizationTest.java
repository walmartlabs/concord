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
import com.walmartlabs.concord.agent.cfg.AgentConfiguration;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentHeader;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentSerializer;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentStatus;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ProcessLogCharacterizationTest {

    @Test
    public void nonSegmentedRunnerOutputIsDeliveredBeforeShutdownCompletes() throws Exception {
        var appender = new RecordingLogAppender();
        var log = newFactory(Files.createTempDirectory("non-segmented"), appender)
                .createRedirectedLog(UUID.randomUUID(), false);

        var stop = new AtomicBoolean(false);
        var failure = new AtomicReference<Throwable>();
        var drainThread = startDrainThread(log, stop, failure);

        log.log(new ByteArrayInputStream("hello ".getBytes(StandardCharsets.UTF_8)));
        await(() -> appender.systemLog().contains("hello "), "first non-segmented chunk");

        log.log(new ByteArrayInputStream("world".getBytes(StandardCharsets.UTF_8)));
        stop.set(true);

        drainThread.join(Duration.ofSeconds(2).toMillis());

        assertFalse(drainThread.isAlive());
        assertNull(failure.get());
        assertEquals("hello world", appender.systemLog());
    }

    @Test
    public void segmentedRunnerOutputKeepsChunkBoundariesAndMapsInvalidBytesToSystemSegmentZero() throws Exception {
        var appender = new RecordingLogAppender();
        var log = newFactory(Files.createTempDirectory("segmented"), appender)
                .createRedirectedLog(UUID.randomUUID(), true);

        var header = segmentHeader(1, 5, 0, 0, LogSegmentStatus.RUNNING);
        var payload = "hello".getBytes(StandardCharsets.UTF_8);
        var segment2 = segmentBytes(2, "bye", 0, 0, LogSegmentStatus.RUNNING);

        var stop = new AtomicBoolean(false);
        var failure = new AtomicReference<Throwable>();
        var drainThread = startDrainThread(log, stop, failure);

        log.log(new ByteArrayInputStream(copyOfRange(header, 0, 4)));
        Thread.sleep(30);

        log.log(new ByteArrayInputStream(Bytes.concat(
                copyOfRange(header, 4, header.length),
                copyOfRange(payload, 0, 3))));
        await(() -> "hel".equals(appender.segmentLog(1)), "partial segmented payload");

        log.log(new ByteArrayInputStream(copyOfRange(payload, 3, payload.length)));
        await(() -> "hello".equals(appender.segmentLog(1)), "completed segmented payload");

        log.log(new ByteArrayInputStream("trash".getBytes(StandardCharsets.UTF_8)));
        await(() -> "trash".equals(appender.segmentLog(0)), "system segment zero fallback");

        log.log(new ByteArrayInputStream(segment2));
        stop.set(true);
        drainThread.join(Duration.ofSeconds(2).toMillis());

        assertFalse(drainThread.isAlive());
        assertNull(failure.get());
        assertEquals("hello", appender.segmentLog(1));
        assertEquals("trash", appender.segmentLog(0));
        assertEquals("bye", appender.segmentLog(2));
    }

    @Test
    public void segmentedRunnerOutputPropagatesCountersAndFinalStatus() throws Exception {
        var appender = new RecordingLogAppender();
        var log = newFactory(Files.createTempDirectory("segmented-status"), appender)
                .createRedirectedLog(UUID.randomUUID(), true);

        var running = segmentBytes(7, "hello", 2, 1, LogSegmentStatus.RUNNING);
        var done = segmentBytes(7, "", 0, 0, LogSegmentStatus.OK);

        var stop = new AtomicBoolean(false);
        var failure = new AtomicReference<Throwable>();
        var drainThread = startDrainThread(log, stop, failure);

        log.log(new ByteArrayInputStream(Bytes.concat(running, done)));
        stop.set(true);
        drainThread.join(Duration.ofSeconds(2).toMillis());

        assertFalse(drainThread.isAlive());
        assertNull(failure.get());
        assertEquals("hello", appender.segmentLog(7));
        assertEquals(1, appender.updates().size());
        assertEquals(new RecordingLogAppender.SegmentUpdate(7, new LogSegmentStats(LogSegmentStatus.OK, 2, 1)),
                appender.updates().get(0));
    }

    @Test
    public void shutdownWaitsForAnInFlightChunkToReachTheSink() throws Exception {
        var dir = Files.createTempDirectory("drain-stop");
        var chunkDelivered = new CountDownLatch(1);
        var releaseChunk = new CountDownLatch(1);
        var delivered = new AtomicReference<String>();

        var log = new RedirectedProcessLog(dir, 5L, chunk -> {
            delivered.set(new String(chunk.bytes(), 0, chunk.len(), StandardCharsets.UTF_8));
            chunkDelivered.countDown();
            try {
                assertTrue(releaseChunk.await(1, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        });

        var stop = new AtomicBoolean(false);
        var failure = new AtomicReference<Throwable>();
        var drainThread = startDrainThread(log, stop, failure);

        log.log(new ByteArrayInputStream("queued".getBytes(StandardCharsets.UTF_8)));
        assertTrue(chunkDelivered.await(1, TimeUnit.SECONDS));

        stop.set(true);
        assertTrue(drainThread.isAlive());

        releaseChunk.countDown();
        drainThread.join(Duration.ofSeconds(2).toMillis());

        assertFalse(drainThread.isAlive());
        assertNull(failure.get());
        assertEquals("queued", delivered.get());
    }

    @Test
    public void stopSignalDoesNotDropBufferedBytesThatWereAlreadyReadFromProcess() throws Exception {
        var dir = Files.createTempDirectory("drain-buffered-stop");
        var firstChunkDelivered = new CountDownLatch(1);
        var releaseFirstChunk = new CountDownLatch(1);
        var chunks = new AtomicReference<String[]>();

        var payload = "a".repeat(8192) + "tail";
        chunks.set(new String[2]);

        var chunkIndex = new AtomicReference<Integer>(0);
        var log = new RedirectedProcessLog(dir, 5L, chunk -> {
            var currentIndex = chunkIndex.getAndSet(chunkIndex.get() + 1);
            chunks.get()[currentIndex] = new String(chunk.bytes(), 0, chunk.len(), StandardCharsets.UTF_8);
            if (currentIndex == 0) {
                firstChunkDelivered.countDown();
                try {
                    assertTrue(releaseFirstChunk.await(1, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
        });

        var stop = new AtomicBoolean(false);
        var failure = new AtomicReference<Throwable>();
        var drainThread = startDrainThread(log, stop, failure);

        log.log(new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)));
        assertTrue(firstChunkDelivered.await(1, TimeUnit.SECONDS));

        stop.set(true);
        releaseFirstChunk.countDown();
        drainThread.join(Duration.ofSeconds(2).toMillis());

        assertFalse(drainThread.isAlive());
        assertNull(failure.get());
        assertEquals("a".repeat(8192), chunks.get()[0]);
        assertEquals("tail", chunks.get()[1]);
    }

    private static ProcessLogFactory newFactory(Path logDir, LogAppender appender) {
        var cfg = mock(AgentConfiguration.class);
        when(cfg.getLogDir()).thenReturn(logDir);
        when(cfg.getLogMaxDelay()).thenReturn(5L);
        return new ProcessLogFactory(cfg, appender);
    }

    private static Thread startDrainThread(RedirectedProcessLog log, AtomicBoolean stop, AtomicReference<Throwable> failure) {
        var t = new Thread(() -> {
            try {
                log.run(stop::get);
            } catch (Throwable e) {
                failure.set(e);
            }
        });
        t.start();
        return t;
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

    private static void await(Check check, String description) throws Exception {
        var deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (System.nanoTime() < deadline) {
            if (check.ok()) {
                return;
            }
            Thread.sleep(10);
        }

        throw new AssertionError("Timed out waiting for " + description);
    }

    @FunctionalInterface
    private interface Check {

        boolean ok();
    }
}
