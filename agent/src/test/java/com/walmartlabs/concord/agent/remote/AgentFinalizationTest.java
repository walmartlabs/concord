package com.walmartlabs.concord.agent.remote;

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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.slf4j.LoggerFactory;
import com.sun.net.httpserver.HttpServer;
import com.walmartlabs.concord.agent.*;
import com.walmartlabs.concord.agent.cfg.AgentConfiguration;
import com.walmartlabs.concord.agent.executors.JobExecutor;
import com.walmartlabs.concord.agent.executors.runner.DefaultDependencies;
import com.walmartlabs.concord.agent.executors.runner.ProcessPool;
import com.walmartlabs.concord.agent.executors.runner.RunnerJob;
import com.walmartlabs.concord.agent.executors.runner.RunnerJobExecutor;
import com.walmartlabs.concord.agent.guice.AgentImportManager;
import com.walmartlabs.concord.agent.logging.*;
import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.dependencymanager.DependencyManager;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentHeader;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentSerializer;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.zip.ZipInputStream;
import com.walmartlabs.concord.sdk.Constants;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Timeout(25)
class AgentFinalizationTest {

    @TempDir
    Path dir;

    private final UUID instanceId = UUID.randomUUID();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final List<String> statuses = new CopyOnWriteArrayList<>();
    private final List<String> events = new CopyOnWriteArrayList<>();
    private final Map<String, byte[]> savedState = new ConcurrentHashMap<>();
    private volatile int attachmentResponse = 204;
    private volatile String serverStatus;
    private final List<String> contents = new CopyOnWriteArrayList<>();
    private final List<String> stats = new CopyOnWriteArrayList<>();
    private final AtomicInteger pings = new AtomicInteger();
    private final AtomicInteger attachmentRequests = new AtomicInteger();
    private final AtomicLong lastPing = new AtomicLong();
    private final CountDownLatch dataRequested = new CountDownLatch(1);
    private final CountDownLatch releaseData = new CountDownLatch(1);
    private HttpServer server;
    private ApiClient client;
    private FinalizationHeartbeat heartbeat;
    private Process process;
    private ProcessHandle processHandle;
    private final AtomicBoolean processAlive = new AtomicBoolean();
    private volatile boolean blockData;
    private volatile boolean blockResponseBody;
    private volatile boolean blockStats;
    private volatile boolean slowFinalization;
    private volatile int dataResponse = 204;
    private RuntimeException logAppendFailure;
    private volatile String blockedFinalizationPath;
    private boolean blockAttachmentUpload;
    private final CountDownLatch finalizationRequested = new CountDownLatch(1);
    private final AtomicInteger finalizationRequests = new AtomicInteger();

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            try {
                var path = exchange.getRequestURI().getPath();
                if (blockAttachmentUpload && path.endsWith("/attachment")) {
                    attachmentRequests.incrementAndGet();
                    finalizationRequested.countDown();
                    releaseData.await();
                }
                var bytes = exchange.getRequestBody().readAllBytes();
                var body = new String(bytes, StandardCharsets.UTF_8);
                var code = 204;
                if (path.endsWith("/attachment")) {
                    attachmentRequests.incrementAndGet();
                }
                if (blockedFinalizationPath != null && path.endsWith(blockedFinalizationPath)
                        && (!blockedFinalizationPath.equals("/log")
                        || body.contains("Process finished with:") || body.contains("Process exit code:") || body.contains("Not all process logs were delivered"))) {
                    finalizationRequests.incrementAndGet();
                    exchange.sendResponseHeaders(200, 2);
                    exchange.getResponseBody().write('{');
                    exchange.getResponseBody().flush();
                    finalizationRequested.countDown();
                    releaseData.await();
                    if (path.endsWith("/attachment")) {
                        saveAttachments(bytes);
                    } else if (path.endsWith("/status")) {
                        acceptStatus(body);
                    }
                    exchange.getResponseBody().write('}');
                    return;
                }
                if (path.endsWith("/ping")) {
                    lastPing.set(System.nanoTime());
                    pings.incrementAndGet();
                } else if (path.contains("/log/segment/")) {
                    if (path.endsWith("/data")) {
                        dataRequested.countDown();
                        if (blockResponseBody) {
                            exchange.sendResponseHeaders(200, 2);
                            exchange.getResponseBody().write('{');
                            exchange.getResponseBody().flush();
                            releaseData.await();
                            exchange.getResponseBody().write('}');
                            return;
                        }
                        if (blockData) {
                            releaseData.await();
                        }
                        if (slowFinalization) {
                            Thread.sleep(800);
                        }
                        code = dataResponse;
                        if (slowFinalization && System.nanoTime() - lastPing.get() > TimeUnit.MILLISECONDS.toNanos(400)) {
                            code = 401;
                        }
                        if (code == 204) {
                            contents.add(body);
                            events.add("stdout");
                        }
                    } else {
                        if (blockStats) {
                            dataRequested.countDown();
                            releaseData.await();
                        }
                        stats.add(body);
                    }
                } else if (path.endsWith("/attachment")) {
                    code = attachmentResponse;
                    if (code == 204) {
                        saveAttachments(bytes);
                    }
                } else if (path.endsWith("/log")) {
                    events.add(body);
                } else if (path.endsWith("/status")) {
                    if (slowFinalization) {
                        Thread.sleep(800);
                    }
                    if (code == 204) {
                        acceptStatus(body);
                    }
                }
                exchange.sendResponseHeaders(code, -1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        client = new com.walmartlabs.concord.client2.DefaultApiClientFactory("http://127.0.0.1:" + server.getAddress().getPort()).create()
                .setReadTimeout(Duration.ofSeconds(5));
        heartbeat = new FinalizationHeartbeat(instanceId, new ProcessHeartbeatApi(client), Duration.ofMillis(30));
        process = mock(Process.class);
        processHandle = mock(ProcessHandle.class);
        when(process.toHandle()).thenReturn(processHandle);
        when(process.isAlive()).thenAnswer(invocation -> processAlive.get());
        when(processHandle.isAlive()).thenAnswer(invocation -> processAlive.get());
        when(processHandle.destroy()).thenAnswer(invocation -> processAlive.getAndSet(false));
        when(processHandle.destroyForcibly()).thenAnswer(invocation -> processAlive.getAndSet(false));
        when(process.onExit()).thenReturn(CompletableFuture.completedFuture(process));
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(segment("last runner message")));
    }

    @AfterEach
    void tearDown() {
        releaseData.countDown();
        heartbeat.close();
        server.stop(0);
        executor.shutdownNow();
    }

    @Test
    void keepsSessionAliveDuringSlowLogsAttachmentsAndStatus() throws Exception {
        slowFinalization = true;
        var attachments = mock(AttachmentsUploader.class);
        doAnswer(invocation -> {
            var before = pings.get();
            Thread.sleep(800);
            assertTrue(pings.get() > before, "heartbeat must cover attachments");
            return null;
        }).when(attachments).upload(eq(instanceId), any());
        worker(Duration.ofSeconds(5), attachments).run();
        assertEquals(List.of("last runner message"), contents);
        assertEquals(1, stats.size());
        assertTrue(stats.getFirst().contains("OK"));
        assertEquals(1, statuses.size());
        assertTrue(statuses.getFirst().contains("FINISHED"));
        assertTrue(pings.get() >= 3);
        assertFalse(Files.exists(dir.resolve("logs").resolve(instanceId.toString()).resolve("system.log")));
        assertRunnerWasNotDestroyed();
    }

    @Test
    void drainDeadlineDoesNotFailSuccessfulRunner() throws Exception {
        blockData = true;
        var worker = worker(Duration.ofMillis(200), mock(AttachmentsUploader.class));
        var execution = executor.submit(worker);
        assertTrue(dataRequested.await(5, TimeUnit.SECONDS));
        execution.get(5, TimeUnit.SECONDS);
        assertEquals(1, statuses.size());
        assertTrue(statuses.getFirst().contains("FINISHED"));
        assertTrue(stats.isEmpty());
        assertRunnerWasNotDestroyed();
    }

    @Test
    void deadlineAlsoStopsReadingAnIncompleteResponseBody() throws Exception {
        blockResponseBody = true;
        var execution = executor.submit(worker(Duration.ofMillis(200), mock(AttachmentsUploader.class)));
        assertTrue(dataRequested.await(5, TimeUnit.SECONDS));
        execution.get(5, TimeUnit.SECONDS);
        assertEquals(1, statuses.size());
        assertTrue(statuses.getFirst().contains("FINISHED"));
    }

    @Test
    void deadlineAlsoStopsSegmentUpdates() throws Exception {
        blockStats = true;
        var execution = executor.submit(worker(Duration.ofMillis(200), mock(AttachmentsUploader.class)));
        execution.get(5, TimeUnit.SECONDS);
        assertEquals(1, statuses.size());
        assertTrue(statuses.getFirst().contains("FINISHED"));
    }

    @Test
    void unexpectedSenderFailureIsReportedBeforeRunnerExit() throws Exception {
        logAppendFailure = new IllegalStateException("unexpected segment failure");
        var exit = new CompletableFuture<Process>();
        var releaseRunner = new CountDownLatch(1);
        when(process.onExit()).thenReturn(exit);
        processAlive.set(true);
        when(process.waitFor()).thenAnswer(invocation -> {
            releaseRunner.await();
            return 0;
        });
        var warned = new CountDownLatch(1);
        var events = new CopyOnWriteArrayList<ILoggingEvent>();
        var logger = (Logger) LoggerFactory.getLogger("com.walmartlabs.concord.agent.executors.runner.RunnerLogStream");
        var appender = new AppenderBase<ILoggingEvent>() {
            @Override
            protected void append(ILoggingEvent event) {
                events.add(event);
                if (event.getFormattedMessage().contains("sender failed unexpectedly")) {
                    warned.countDown();
                }
            }
        };
        appender.start();
        logger.addAppender(appender);
        try {
            var execution = executor.submit(worker(Duration.ofSeconds(2), mock(AttachmentsUploader.class)));
            try {
                assertTrue(warned.await(5, TimeUnit.SECONDS), "failure must be reported while the runner is alive");
                var warning = events.stream().filter(e -> e.getFormattedMessage().contains("sender failed unexpectedly"))
                        .findFirst().orElseThrow();
                assertEquals(Level.WARN, warning.getLevel());
                assertTrue(warning.getFormattedMessage().contains(instanceId.toString()));
                assertNotNull(warning.getThrowableProxy());
                assertEquals(logAppendFailure.getMessage(), warning.getThrowableProxy().getMessage());
                assertTrue(warning.getThrowableProxy().getStackTraceElementProxyArray().length > 0);
                assertFalse(execution.isDone());
                assertTrue(process.isAlive());
                assertRunnerWasNotDestroyed();
                assertTrue(statuses.isEmpty());
                assertFalse(events.stream().anyMatch(e -> e.getFormattedMessage().contains("unprocessed local bytes")));
            } finally {
                processAlive.set(false);
                exit.complete(process);
                releaseRunner.countDown();
            }
            execution.get(5, TimeUnit.SECONDS);
            assertRunnerWasNotDestroyed();
            assertEquals(1, statuses.size());
            assertTrue(statuses.getFirst().contains("FINISHED"));
            assertEquals(1, events.stream().filter(e -> e.getFormattedMessage().contains("sender failed unexpectedly")).count());
            assertTrue(events.stream().anyMatch(e -> e.getFormattedMessage().contains(
                    "unprocessed local bytes: " + segment("last runner message").length)));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void authorizationFailureDoesNotKillAnExecutingRunner() throws Exception {
        dataResponse = 401;
        var exit = new CompletableFuture<Process>();
        var releaseRunner = new CountDownLatch(1);
        when(process.onExit()).thenReturn(exit);
        processAlive.set(true);
        when(process.waitFor()).thenAnswer(invocation -> {
            releaseRunner.await();
            return 0;
        });
        var execution = executor.submit(worker(Duration.ofSeconds(2), mock(AttachmentsUploader.class)));
        try {
            assertTrue(dataRequested.await(5, TimeUnit.SECONDS));
            assertFalse(execution.isDone());
            assertEquals(0, pings.get());
            assertTrue(process.isAlive());
            assertTrue(processHandle.isAlive());
            assertRunnerWasNotDestroyed();
        } finally {
            processAlive.set(false);
            exit.complete(process);
            releaseRunner.countDown();
        }
        execution.get(5, TimeUnit.SECONDS);
        assertRunnerWasNotDestroyed();
        assertEquals(1, statuses.size());
        assertTrue(statuses.getFirst().contains("FINISHED"));
    }

    @Test
    void runnerFailureStillReportsFailed() throws Exception {
        when(process.waitFor()).thenReturn(1);
        worker(Duration.ofSeconds(2), mock(AttachmentsUploader.class)).run();
        assertEquals(List.of("last runner message"), contents);
        assertEquals(1, statuses.size());
        assertTrue(statuses.getFirst().contains("FAILED"));
        var diagnostic = events.stream().filter(s -> s.contains("Process exit code:")).findFirst().orElseThrow();
        assertTrue(events.indexOf("stdout") < events.indexOf(diagnostic));
        assertTrue(events.indexOf(diagnostic) < events.indexOf("status"));
    }

    @Test
    void statusReportingFailureDoesNotSendDifferentStatus() throws Exception {
        var job = mock(JobInstance.class);
        var statusUpdater = mock(ProcessStatusUpdater.class);
        doThrow(new IllegalStateException("response lost")).when(statusUpdater)
                .update(instanceId, ProcessEntry.StatusEnum.FINISHED);
        var req = request();
        var worker = new Worker(mock(RepositoryManager.class), mock(AgentImportManager.class),
                ignored -> job, ignored -> {}, mock(StateFetcher.class), statusUpdater,
                mock(ProcessLog.class), req, heartbeat);
        assertThrows(IllegalStateException.class, worker::run);
        verify(statusUpdater).update(instanceId, ProcessEntry.StatusEnum.FINISHED);
        verifyNoMoreInteractions(statusUpdater);
    }

    @Test
    void exitStartsHeartbeatBeforeStdoutCopyHasFinished() throws Exception {
        var stdoutEntered = new CountDownLatch(1);
        var releaseStdout = new CountDownLatch(1);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(segment("tail")) {
            @Override
            public synchronized int read(byte[] bytes, int offset, int length) {
                stdoutEntered.countDown();
                try {
                    releaseStdout.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                return super.read(bytes, offset, length);
            }
        });
        var worker = worker(Duration.ofSeconds(5), mock(AttachmentsUploader.class));
        var execution = executor.submit(worker);
        try {
            assertTrue(stdoutEntered.await(5, TimeUnit.SECONDS));
            var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (pings.get() < 2 && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertTrue(pings.get() >= 2, "heartbeat must not wait for stdout EOF");
        } finally {
            releaseStdout.countDown();
        }
        execution.get(5, TimeUnit.SECONDS);
        assertEquals(List.of("tail"), contents);
    }

    @Test
    void heartbeatIsStoppedBeforeCompletionCallback() throws Exception {
        var callbackInvoked = new java.util.concurrent.atomic.AtomicBoolean();
        var worker = worker(Duration.ofSeconds(2), mock(AttachmentsUploader.class), status -> {
            callbackInvoked.set(true);
            assertEquals(ProcessEntry.StatusEnum.FINISHED, status);
            assertEquals(1, statuses.size());
            var count = pings.get();
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
            assertEquals(count, pings.get(), "heartbeat must already be stopped during the callback");
        });
        worker.run();
        assertTrue(callbackInvoked.get());
    }

    @Test
    void logDeadlineStillUploadsSuspendStateBeforeReportingSuccess() throws Exception {
        blockResponseBody = true;
        var worker = worker(Duration.ofMillis(200), new AttachmentsUploader(client, Duration.ofSeconds(5)));
        writeSuspendState();
        var execution = executor.submit(worker);
        assertTrue(dataRequested.await(5, TimeUnit.SECONDS));
        execution.get(5, TimeUnit.SECONDS);
        assertTrue(contents.isEmpty());
        assertTrue(stats.isEmpty());
        assertSavedSuspendState();
        assertEquals("SUSPENDED", serverStatus);
        assertTrue(events.indexOf("attachments") < events.indexOf("status"));
        assertRunnerWasNotDestroyed();
    }

    @Test
    void rejectedAttachmentsMustNotReportFinished() throws Exception {
        attachmentResponse = 400;
        var worker = worker(Duration.ofMillis(200), new AttachmentsUploader(client, Duration.ofSeconds(5)));
        writeSuspendState();
        worker.run();
        assertTrue(savedState.isEmpty());
        assertEquals(1, statuses.size());
        assertTrue(statuses.getFirst().contains("FAILED"));
        assertEquals("FAILED", serverStatus);
    }

    @Test
    void fifoAttachmentFailsAndStopsHeartbeat() throws Exception {
        assertSpecialAttachmentRejected(false);
    }

    @Test
    void symlinkToFifoAttachmentFailsAndStopsHeartbeat() throws Exception {
        assertSpecialAttachmentRejected(true);
    }

    private void assertSpecialAttachmentRejected(boolean symlink) throws Exception {
        var worker = worker(Duration.ofSeconds(1), new AttachmentsUploader(client, Duration.ofMillis(100)));
        writeSuspendState();
        var attachments = dir.resolve("work").resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME);
        var fifo = symlink ? dir.resolve("output.pipe") : attachments.resolve("output.pipe");
        assertEquals(0, new ProcessBuilder("mkfifo", fifo.toString()).start().waitFor());
        if (symlink) {
            Files.createSymbolicLink(attachments.resolve("output.pipe"), fifo);
        }
        var execution = executor.submit(worker);
        try {
            execution.get(5, TimeUnit.SECONDS);
            assertEquals(List.of("FAILED"), statuses);
            assertEquals(0, attachmentRequests.get());
            assertTrue(savedState.isEmpty());
            assertFalse(Files.exists(dir.resolve("work")));
            var count = pings.get();
            Thread.sleep(150);
            assertEquals(count, pings.get(), "heartbeat must stop after rejecting the attachment");
        } finally {
            if (!execution.isDone()) {
                // Unblock a regressed implementation so the test does not leak a stuck worker.
                try (var ignored = FileChannel.open(fifo, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                    execution.get(5, TimeUnit.SECONDS);
                }
            }
        }
    }

    @Test
    void symlinkToRegularAttachmentStillUploadsWithSuspendState() throws Exception {
        var worker = worker(Duration.ofSeconds(1), new AttachmentsUploader(client, Duration.ofSeconds(5)));
        writeSuspendState();
        var source = Files.writeString(dir.resolve("source.txt"), "attachment contents");
        Files.createSymbolicLink(dir.resolve("work").resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve("linked.txt"), source);
        executor.submit(worker).get(5, TimeUnit.SECONDS);
        assertArrayEquals("attachment contents".getBytes(StandardCharsets.UTF_8), savedState.get("linked.txt"));
        assertSavedSuspendState();
        assertEquals("SUSPENDED", serverStatus);
    }

    @Test
    void logDeadlineDoesNotInterruptSlowStateUpload() throws Exception {
        blockedFinalizationPath = "/attachment";
        blockResponseBody = true;
        var worker = worker(Duration.ofMillis(200), new AttachmentsUploader(client, Duration.ofSeconds(5)));
        writeSuspendState();
        var execution = executor.submit(worker);
        try {
            assertTrue(finalizationRequested.await(5, TimeUnit.SECONDS));
            var before = pings.get();
            Thread.sleep(500);
            assertTrue(pings.get() > before, "heartbeat covers state upload after log deadline");
            assertFalse(execution.isDone());
            assertTrue(statuses.isEmpty(), "state must be confirmed before reporting success");
        } finally {
            releaseData.countDown();
        }
        execution.get(5, TimeUnit.SECONDS);
        assertSavedSuspendState();
        assertEquals("SUSPENDED", serverStatus);
        assertEquals(1, finalizationRequests.get());
    }

    @Test
    void finalDiagnosticsAreDeliveredAfterStdoutAndBeforeStatus() throws Exception {
        worker(Duration.ofSeconds(2), new AttachmentsUploader(client, Duration.ofSeconds(5))).run();
        var diagnostic = events.stream().filter(s -> s.contains("Process finished with: 0")).findFirst().orElseThrow();
        assertTrue(events.indexOf("stdout") < events.indexOf(diagnostic));
        assertTrue(events.indexOf(diagnostic) < events.indexOf("status"));
    }

    @Test
    void rejectedLogAuthorizationDoesNotSuppressStateOrStatus() throws Exception {
        dataResponse = 401;
        var worker = worker(Duration.ofSeconds(2), new AttachmentsUploader(client, Duration.ofSeconds(5)));
        writeSuspendState();
        worker.run();
        assertTrue(contents.isEmpty());
        assertTrue(stats.isEmpty());
        assertSavedSuspendState();
        assertEquals("SUSPENDED", serverStatus);
        assertRunnerWasNotDestroyed();
    }

    @Test
    void incompleteFinalDiagnosticIsBoundedAndStillSavesState() throws Exception {
        blockResponseBody = true;
        blockedFinalizationPath = "/log";
        var worker = worker(Duration.ofMillis(300), new AttachmentsUploader(client, Duration.ofSeconds(3)));
        writeSuspendState();
        var execution = executor.submit(worker);
        assertTrue(finalizationRequested.await(5, TimeUnit.SECONDS));
        execution.get(8, TimeUnit.SECONDS);
        assertEquals(1, finalizationRequests.get(), "cancelled diagnostics must not retry");
        assertSavedSuspendState();
        assertEquals("SUSPENDED", serverStatus);
        assertRunnerWasNotDestroyed();
    }

    @Test
    void attachmentBudgetAlsoCancelsBlockedRequestUpload() throws Exception {
        blockAttachmentUpload = true;
        var worker = worker(Duration.ofSeconds(2), new AttachmentsUploader(client, Duration.ofSeconds(2)));
        writeSuspendState();
        var archiveData = new byte[16 * 1024 * 1024];
        new java.util.Random(1).nextBytes(archiveData);
        Files.write(dir.resolve("work").resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME).resolve("large.bin"), archiveData);
        var execution = executor.submit(worker);
        assertTrue(finalizationRequested.await(5, TimeUnit.SECONDS));
        execution.get(5, TimeUnit.SECONDS);
        assertEquals(1, attachmentRequests.get(), "cancelled upload must not retry");
        assertEquals(List.of("FAILED"), statuses);
        assertFalse(Files.exists(dir.resolve("work")), "cleanup must follow actual upload cancellation");
    }

    @Test
    void attachmentDeadlineCannotTurnSuspendIntoFinished() throws Exception {
        blockedFinalizationPath = "/attachment";
        var worker = worker(Duration.ofSeconds(2), new AttachmentsUploader(client, Duration.ofMillis(300)));
        writeSuspendState();
        var execution = executor.submit(worker);
        assertTrue(finalizationRequested.await(5, TimeUnit.SECONDS));
        execution.get(5, TimeUnit.SECONDS);
        assertEquals(1, finalizationRequests.get(), "cancelled uploads must not retry");
        assertEquals(1, statuses.size());
        assertTrue(statuses.getFirst().contains("FAILED"));
        assertEquals("FAILED", serverStatus);
    }

    @Test
    void cancelAfterRunnerExitWaitsForStateAndReportsCancelled() throws Exception {
        blockedFinalizationPath = "/attachment";
        var worker = worker(Duration.ofMillis(200), new AttachmentsUploader(client, Duration.ofSeconds(3)));
        writeSuspendState();
        var execution = executor.submit(worker);
        try {
            assertTrue(finalizationRequested.await(5, TimeUnit.SECONDS));
            worker.cancel();
            var before = pings.get();
            Thread.sleep(150);
            assertFalse(execution.isDone());
            assertTrue(pings.get() > before);
        } finally {
            releaseData.countDown();
        }
        execution.get(5, TimeUnit.SECONDS);
        assertSavedSuspendState();
        assertEquals(List.of("CANCELLED"), statuses);
    }

    @Test
    void cancelDuringLogDrainStillUploadsStateAndReportsCancelled() throws Exception {
        blockResponseBody = true;
        var worker = worker(Duration.ofMillis(400), new AttachmentsUploader(client, Duration.ofSeconds(3)));
        writeSuspendState();
        var execution = executor.submit(worker);
        assertTrue(dataRequested.await(5, TimeUnit.SECONDS));
        worker.cancel();
        execution.get(5, TimeUnit.SECONDS);
        assertSavedSuspendState();
        assertEquals(List.of("CANCELLED"), statuses);
        assertTrue(events.stream().anyMatch(s -> s.contains("Not all process logs were delivered")));
    }

    @Test
    void heartbeatStopsBeforeStatusEvenWhenItsResponseHangs() throws Exception {
        blockedFinalizationPath = "/status";
        var execution = executor.submit(worker(Duration.ofSeconds(2), new AttachmentsUploader(client, Duration.ofSeconds(3))));
        try {
            assertTrue(finalizationRequested.await(5, TimeUnit.SECONDS));
            var before = pings.get();
            Thread.sleep(200);
            assertEquals(before, pings.get());
            assertFalse(execution.isDone());
        } finally {
            releaseData.countDown();
        }
        execution.get(5, TimeUnit.SECONDS);
        assertEquals(List.of("FINISHED"), statuses);
    }

    @Test
    void failedRunnerRemainsFailedWhenItsFinalDiagnosticTimesOut() throws Exception {
        blockedFinalizationPath = "/log";
        when(process.waitFor()).thenReturn(1);
        var worker = worker(Duration.ofMillis(300), new AttachmentsUploader(client, Duration.ofSeconds(3)));
        var execution = executor.submit(worker);
        assertTrue(finalizationRequested.await(5, TimeUnit.SECONDS));
        execution.get(8, TimeUnit.SECONDS);
        assertEquals(1, finalizationRequests.get());
        assertEquals("FAILED", serverStatus);
    }

    @Test
    void attachmentBudgetIncludesRetryBackoffAndDoesNotResetForEachAttempt() throws Exception {
        attachmentResponse = 500;
        var worker = worker(Duration.ofSeconds(2), new AttachmentsUploader(client, Duration.ofMillis(300)));
        writeSuspendState();
        executor.submit(worker).get(5, TimeUnit.SECONDS);
        assertEquals(1, attachmentRequests.get(), "the total budget must cancel the retry wait");
        assertEquals("FAILED", serverStatus);
    }

    private void writeSuspendState() throws Exception {
        var state = Files.createDirectories(dir.resolve("work").resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(Constants.Files.JOB_STATE_DIR_NAME));
        Files.writeString(state.resolve(Constants.Files.SUSPEND_MARKER_FILE_NAME), "");
        Files.write(state.resolve("snapshot.bin"), new byte[]{0, 1, 2, 3, -1});
    }

    private void assertSavedSuspendState() {
        assertTrue(savedState.containsKey(Constants.Files.JOB_STATE_DIR_NAME + "/" + Constants.Files.SUSPEND_MARKER_FILE_NAME));
        assertArrayEquals(new byte[]{0, 1, 2, 3, -1}, savedState.get(Constants.Files.JOB_STATE_DIR_NAME + "/snapshot.bin"));
    }

    private void saveAttachments(byte[] bytes) throws IOException {
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (!entry.isDirectory()) {
                    savedState.put(entry.getName(), zip.readAllBytes());
                }
            }
        }
        events.add("attachments");
    }

    private void acceptStatus(String body) {
        statuses.add(body);
        // Model ProcessManager's existing FINISHED -> SUSPENDED contract. The actual
        // archive must have been accepted, not merely an upload method invoked.
        if (body.contains("FINISHED")) {
            serverStatus = savedState.containsKey(Constants.Files.JOB_STATE_DIR_NAME + "/" + Constants.Files.SUSPEND_MARKER_FILE_NAME)
                    ? "SUSPENDED" : "FINISHED";
        } else {
            serverStatus = body;
        }
        events.add("status");
    }

    private void assertRunnerWasNotDestroyed() {
        verify(processHandle, never()).destroy();
        verify(processHandle, never()).destroyForcibly();
        verify(process, never()).destroy();
        verify(process, never()).destroyForcibly();
    }

    private Worker worker(Duration drainTimeout, AttachmentsUploader attachments) throws Exception {
        return worker(drainTimeout, attachments, ignored -> {});
    }

    private Worker worker(Duration drainTimeout, AttachmentsUploader attachments, Worker.CompletionCallback callback) throws Exception {
        var cfg = mock(RunnerJobExecutor.RunnerJobExecutorConfiguration.class);
        when(cfg.javaCmd()).thenReturn(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        when(cfg.agentId()).thenReturn("test-agent");
        when(cfg.serverApiBaseUrl()).thenReturn("http://127.0.0.1:" + server.getAddress().getPort());
        when(cfg.dependencyCacheDir()).thenReturn(dir.resolve("deps"));
        when(cfg.extraDockerVolumes()).thenReturn(List.of());
        when(cfg.exposeDockerDaemon()).thenReturn(false);
        when(cfg.segmentedLogs()).thenReturn(true);
        when(cfg.logDrainTimeout()).thenReturn(drainTimeout);
        when(cfg.dependencyResolveTimeout()).thenReturn(null);
        var agentCfg = mock(AgentConfiguration.class);
        when(agentCfg.getLogDir()).thenReturn(dir.resolve("logs"));
        when(agentCfg.getLogMaxDelay()).thenReturn(1L);
        var logAppender = new RemoteLogAppender(client);
        if (logAppendFailure != null) {
            logAppender = spy(logAppender);
            doThrow(logAppendFailure).when(logAppender).appendLog(eq(instanceId), anyLong(), any(byte[].class));
        }
        var logFactory = new ProcessLogFactory(agentCfg, logAppender);
        var deps = mock(DependencyManager.class);
        when(deps.resolve(anyCollection(), any())).thenReturn(List.of());
        var workDir = Files.createDirectory(dir.resolve("work"));
        JobExecutor executor = new RunnerJobExecutor(cfg, deps, mock(DefaultDependencies.class), attachments,
                mock(ProcessPool.class), logFactory, this.executor, heartbeat) {
            @Override
            protected ProcessPool.ProcessEntry buildProcessEntry(RunnerJob job) {
                return new ProcessPool.ProcessEntry(process, workDir);
            }
        };
        return new Worker(mock(RepositoryManager.class), mock(AgentImportManager.class), executor,
                callback, mock(StateFetcher.class),
                new ProcessStatusUpdater("test-agent", new ProcessApi(client)),
                new RemoteProcessLog(instanceId, new RemoteLogAppender(client)), request(), heartbeat);
    }

    private JobRequest request() throws Exception {
        return new JobRequest(JobRequest.Type.RUNNER, instanceId, Files.createDirectory(dir.resolve("payload")),
                null, null, null, null, null, null, null, null) {};
    }

    private static byte[] segment(String message) {
        var bytes = message.getBytes(StandardCharsets.UTF_8);
        var header = LogSegmentHeader.builder().segmentId(1).status(LogSegmentStatus.RUNNING)
                .errorCount(0).warnCount(0).length(bytes.length).build();
        var out = new java.io.ByteArrayOutputStream();
        out.writeBytes(LogSegmentSerializer.serializeHeader(header, bytes.length));
        out.writeBytes(bytes);
        out.writeBytes(LogSegmentSerializer.serializeHeader(LogSegmentHeader.builder()
                .segmentId(1).status(LogSegmentStatus.OK).errorCount(0).warnCount(0).length(0).build()));
        return out.toByteArray();
    }
}
