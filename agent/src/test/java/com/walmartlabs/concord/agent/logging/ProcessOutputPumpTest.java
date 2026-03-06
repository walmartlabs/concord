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

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProcessOutputPumpTest {

    @Test
    public void copiesProcessOutputAcrossMultipleReads() throws Exception {
        var payload = "a".repeat(8192) + "tail";
        var sink = new RecordingSink();
        var pump = new ProcessOutputPump(1000L);

        pump.pump(new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)), sink);

        assertEquals(payload, sink.output());
        assertEquals(1, sink.flushCount());
    }

    @Test
    public void flushesBufferedBytesWithinConfiguredDelayWhileStreamRemainsOpen() throws Exception {
        var input = new PipedInputStream();
        var output = new PipedOutputStream(input);
        var sink = new RecordingSink();
        var pump = new ProcessOutputPump(25L);
        var failure = new AtomicReference<Throwable>();
        var thread = new Thread(() -> {
            try {
                pump.pump(input, sink);
            } catch (Throwable e) {
                failure.set(e);
            }
        });

        thread.start();
        output.write("hello".getBytes(StandardCharsets.UTF_8));
        output.flush();

        assertTrue(sink.awaitFlush(Duration.ofSeconds(1)));
        output.close();
        thread.join(Duration.ofSeconds(1).toMillis());

        assertFalse(thread.isAlive());
        assertNull(failure.get());
        assertEquals("hello", sink.output());
        assertTrue(sink.flushCount() >= 1);
    }

    @Test
    public void zeroDelayFlushesImmediatelyAfterEachWrite() throws Exception {
        var sink = new RecordingSink();
        var pump = new ProcessOutputPump(0L);

        pump.pump(new ChunkedInputStream("hello world".getBytes(StandardCharsets.UTF_8), 5), sink);

        assertEquals("hello world", sink.output());
        assertEquals(3, sink.flushCount());
    }

    @Test
    public void waitsForAnInFlightWriteToCompleteBeforeReturning() throws Exception {
        var sink = new BlockingSink();
        var pump = new ProcessOutputPump(0L);
        var failure = new AtomicReference<Throwable>();
        var thread = new Thread(() -> {
            try {
                pump.pump(new ByteArrayInputStream("queued".getBytes(StandardCharsets.UTF_8)), sink);
            } catch (Throwable e) {
                failure.set(e);
            }
        });

        thread.start();
        assertTrue(sink.awaitWrite(Duration.ofSeconds(1)));
        assertTrue(thread.isAlive());

        sink.release();
        thread.join(Duration.ofSeconds(1).toMillis());

        assertFalse(thread.isAlive());
        assertNull(failure.get());
        assertEquals("queued", sink.output());
        assertEquals(1, sink.flushCount());
    }

    @Test
    public void preservesMultiReadOutputWhileTheFirstWriteIsBlocked() throws Exception {
        var payload = "a".repeat(8192) + "tail";
        var sink = new BlockingSink();
        var pump = new ProcessOutputPump(0L);
        var failure = new AtomicReference<Throwable>();
        var thread = new Thread(() -> {
            try {
                pump.pump(new ChunkedInputStream(payload.getBytes(StandardCharsets.UTF_8), 8192), sink);
            } catch (Throwable e) {
                failure.set(e);
            }
        });

        thread.start();
        assertTrue(sink.awaitWrite(Duration.ofSeconds(1)));

        thread.join(Duration.ofMillis(100).toMillis());
        assertTrue(thread.isAlive());

        sink.release();
        thread.join(Duration.ofSeconds(1).toMillis());

        assertFalse(thread.isAlive());
        assertNull(failure.get());
        assertEquals(payload, sink.output());
        assertEquals(2, sink.flushCount());
    }

    private static class RecordingSink implements ProcessOutputSink {

        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final AtomicInteger flushCount = new AtomicInteger();
        private final CountDownLatch firstFlush = new CountDownLatch(1);

        @Override
        public synchronized void write(byte[] bytes, int offset, int len) {
            out.write(bytes, offset, len);
        }

        @Override
        public synchronized void flush() {
            flushCount.incrementAndGet();
            firstFlush.countDown();
        }

        private boolean awaitFlush(Duration timeout) throws InterruptedException {
            return firstFlush.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        protected synchronized String output() {
            return out.toString(StandardCharsets.UTF_8);
        }

        protected int flushCount() {
            return flushCount.get();
        }
    }

    private static class ChunkedInputStream extends ByteArrayInputStream {

        private final int chunkSize;

        private ChunkedInputStream(byte[] bytes, int chunkSize) {
            super(bytes);
            this.chunkSize = chunkSize;
        }

        @Override
        public synchronized int read(byte[] bytes, int offset, int len) {
            return super.read(bytes, offset, Math.min(len, chunkSize));
        }
    }

    private static class BlockingSink extends RecordingSink {

        private final CountDownLatch writeEntered = new CountDownLatch(1);
        private final CountDownLatch writeReleased = new CountDownLatch(1);

        @Override
        public synchronized void write(byte[] bytes, int offset, int len) {
            super.write(bytes, offset, len);
            writeEntered.countDown();
            try {
                assertTrue(writeReleased.await(1, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }

        private boolean awaitWrite(Duration timeout) throws InterruptedException {
            return writeEntered.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        private void release() {
            writeReleased.countDown();
        }
    }
}
