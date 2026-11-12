package com.walmartlabs.concord.agent.executors.runner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.slf4j.LoggerFactory;
import com.walmartlabs.concord.agent.logging.LogStreamingException;
import com.walmartlabs.concord.agent.logging.RedirectedProcessLog;
import com.walmartlabs.concord.agent.logging.RemoteProcessLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RunnerLogStreamTest {

    @TempDir
    Path dir;

    @Test
    void expectedStopsDoNotReportUnexpectedFailure() throws Exception {
        var events = new java.util.concurrent.CopyOnWriteArrayList<ILoggingEvent>();
        var logger = (Logger) LoggerFactory.getLogger(RunnerLogStream.class);
        var appender = new AppenderBase<ILoggingEvent>() {
            @Override
            protected void append(ILoggingEvent event) {
                events.add(event);
            }
        };
        appender.start();
        logger.addAppender(appender);
        try {
            for (var failure : java.util.List.of(new LogStreamingException("Authorization rejected: HTTP 401"),
                    new LogStreamingException("Authorization rejected: HTTP 403"), new InterruptedException("cancelled"))) {
                var runnerLog = mock(RunnerLog.class);
                doThrow(failure).when(runnerLog).run(any());
                try (var stream = new RunnerLogStream(UUID.randomUUID(), runnerLog, Duration.ofSeconds(2))) {
                    stream.finish();
                }
            }
            var runnerLog = mock(RunnerLog.class);
            doAnswer(invocation -> {
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException e) {
                    // I/O can consume the interrupt and surface a different exception.
                    throw new java.io.IOException("request interrupted", e);
                }
                return null;
            }).when(runnerLog).run(any());
            try (var stream = new RunnerLogStream(UUID.randomUUID(), runnerLog, Duration.ofMillis(50))) {
                    stream.finish();
            }
            assertFalse(events.stream().anyMatch(e -> e.getFormattedMessage().contains("sender failed unexpectedly")));
            assertEquals(4, events.stream().filter(e -> e.getFormattedMessage().contains("unprocessed local bytes")).count());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void drainsAllBytesBeforeCleanup() throws Exception {
        var delivered = new java.io.ByteArrayOutputStream();
        var redirected = new RedirectedProcessLog(dir, 1, chunk -> delivered.write(chunk.bytes(), 0, chunk.len()));
        var bytes = new byte[40000];
        new java.util.Random(1).nextBytes(bytes);
        redirected.log(new ByteArrayInputStream(bytes));
        var runnerLog = new RunnerLog(redirected, mock(RemoteProcessLog.class));
        try (var stream = new RunnerLogStream(UUID.randomUUID(), runnerLog, Duration.ofSeconds(2))) {
            stream.finish();
            assertArrayEquals(bytes, delivered.toByteArray());
            assertEquals(0, runnerLog.remainingBytes());
        }
        runnerLog.delete();
        assertFalse(java.nio.file.Files.exists(dir.resolve("system.log")));
    }

    @Test
    void deadlineStopsSenderWhileFinishing() throws Exception {
        var entered = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        var redirected = new RedirectedProcessLog(dir, 1, chunk -> {
            entered.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        });
        redirected.log(new ByteArrayInputStream(new byte[100]));
        var runnerLog = new RunnerLog(redirected, mock(RemoteProcessLog.class));
        try (var stream = new RunnerLogStream(UUID.randomUUID(), runnerLog, Duration.ofMillis(50))) {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertTrue(stream.finish().contains("deadline"));
            assertTrue(interrupted.await(2, TimeUnit.SECONDS));
            assertDoesNotThrow(() -> { stream.finish(); });
            assertEquals(100, runnerLog.remainingBytes());
        }
    }

    @Test
    void waitsForActualSenderExitAfterInterrupt() throws Exception {
        var entered = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        var allowExit = new CountDownLatch(1);
        var senderExited = new AtomicBoolean();
        var redirected = new RedirectedProcessLog(dir, 1, chunk -> {
            entered.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                interrupted.countDown();
                try {
                    allowExit.await();
                } catch (InterruptedException ignored) {
                    throw new RuntimeException(ignored);
                }
                senderExited.set(true);
                Thread.currentThread().interrupt();
            }
        });
        redirected.log(new ByteArrayInputStream(new byte[100]));
        var runnerLog = new RunnerLog(redirected, mock(RemoteProcessLog.class));
        var stream = new RunnerLogStream(UUID.randomUUID(), runnerLog, Duration.ofMillis(50));
        var finished = new CountDownLatch(1);
        var waiter = Thread.ofVirtual().start(() -> {
            try {
                stream.finish();
                finished.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertTrue(interrupted.await(2, TimeUnit.SECONDS));
            assertFalse(finished.await(50, TimeUnit.MILLISECONDS));
            assertFalse(senderExited.get());
            allowExit.countDown();
            assertTrue(finished.await(2, TimeUnit.SECONDS));
            assertTrue(senderExited.get());
        } finally {
            allowExit.countDown();
            stream.close();
            waiter.join();
        }
    }
}
