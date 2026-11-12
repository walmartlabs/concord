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
import com.walmartlabs.concord.client2.ApiException;
import com.walmartlabs.concord.client2.ProcessHeartbeatApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Timeout(5)
class FinalizationHeartbeatTest {

    @Test
    void stalledHttpPingIsCancelledAndTheNextPingStillRuns() throws Exception {
        for (var partialBody : new boolean[]{false, true}) {
            var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
            var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
            var release = new CountDownLatch(1);
            var secondPing = new CountDownLatch(1);
            var requests = new java.util.concurrent.atomic.AtomicInteger();
            server.setExecutor(executor);
            server.createContext("/", exchange -> {
                try {
                    if (requests.incrementAndGet() == 1) {
                        if (partialBody) {
                            exchange.sendResponseHeaders(200, 2);
                            exchange.getResponseBody().write('{');
                            exchange.getResponseBody().flush();
                        }
                        release.await();
                    } else {
                        exchange.sendResponseHeaders(204, -1);
                        secondPing.countDown();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    exchange.close();
                }
            });
            server.start();
            var client = new com.walmartlabs.concord.client2.DefaultApiClientFactory(
                    "http://127.0.0.1:" + server.getAddress().getPort()).create().setReadTimeout(Duration.ofSeconds(5));
            try (var heartbeat = new FinalizationHeartbeat(UUID.randomUUID(), new ProcessHeartbeatApi(client),
                    Duration.ofMillis(20), Duration.ofMillis(200))) {
                heartbeat.start();
                assertTrue(secondPing.await(2, TimeUnit.SECONDS), "a stalled response must not stop subsequent pings");
                heartbeat.close();
                var count = requests.get();
                Thread.sleep(100);
                assertEquals(count, requests.get());
            } finally {
                release.countDown();
                server.stop(0);
                executor.shutdownNow();
            }
        }
    }

    @Test
    void closedHeartbeatCannotBeStartedByLateExitCallback() throws Exception {
        var api = mock(ProcessHeartbeatApi.class);
        var heartbeat = new FinalizationHeartbeat(UUID.randomUUID(), api, Duration.ofMillis(10));
        heartbeat.close();
        heartbeat.start();
        Thread.sleep(150);
        verifyNoInteractions(api);
    }

    @Test
    void closeInterruptsAnInFlightPingAndPreventsRestart() throws Exception {
        var api = mock(ProcessHeartbeatApi.class);
        var entered = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                interrupted.countDown();
                throw new ApiException(0, "interrupted response body");
            }
            return null;
        }).when(api).pingProcess(any());
        try (var heartbeat = new FinalizationHeartbeat(UUID.randomUUID(), api, Duration.ofMillis(10))) {
            heartbeat.start();
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            heartbeat.close();
            assertTrue(interrupted.await(2, TimeUnit.SECONDS));
            heartbeat.start();
            Thread.sleep(100);
            verify(api, times(1)).pingProcess(any());
        }
    }

    @Test
    void revokedTokenStopsHeartbeatWithoutMisleadingWarnings() throws Exception {
        var logger = (Logger) LoggerFactory.getLogger(FinalizationHeartbeat.class);
        var events = new CopyOnWriteArrayList<ILoggingEvent>();
        var appender = new AppenderBase<ILoggingEvent>() {
            @Override
            protected void append(ILoggingEvent event) {
                events.add(event);
            }
        };
        appender.start();
        logger.addAppender(appender);
        try {
            for (var code : new int[]{401, 403}) {
                var api = mock(ProcessHeartbeatApi.class);
                doThrow(new ApiException(code, "revoked")).when(api).pingProcess(any());
                try (var heartbeat = new FinalizationHeartbeat(UUID.randomUUID(), api, Duration.ofMillis(10))) {
                    heartbeat.start();
                    verify(api, timeout(2000)).pingProcess(any());
                    Thread.sleep(150);
                    verify(api, times(1)).pingProcess(any());
                }
            }
            assertFalse(events.stream().anyMatch(e -> e.getLevel().isGreaterOrEqual(Level.WARN)));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
