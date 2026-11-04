package com.walmartlabs.concord.agent.logging;

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

import com.sun.net.httpserver.HttpServer;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentHeader;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentSerializer;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RemoteLogAppenderTest {

    private HttpServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger responseCode = new AtomicInteger(204);
    private RemoteLogAppender appender;
    private final UUID instanceId = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            requests.add(exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(responseCode.get(), -1);
            exchange.close();
        });
        server.start();
        var client = new com.walmartlabs.concord.client2.DefaultApiClientFactory("http://127.0.0.1:" + server.getAddress().getPort()).create()
                .setReadTimeout(Duration.ofSeconds(2));
        appender = new RemoteLogAppender(client);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void unauthorizedAppendStopsTheRestOfTheChunk() {
        responseCode.set(401);
        var consumer = new SegmentedLogsConsumer(instanceId, appender);
        assertThrows(LogStreamingException.class, () -> consumer.accept(segments(1, 2)));
        assertEquals(1, requests.size());
        assertThrows(LogStreamingException.class, () -> consumer.accept(segments(3)));
        assertEquals(1, requests.size());
    }

    @Test
    void forbiddenStatsStopSubsequentRequests() {
        responseCode.set(403);
        assertThrows(LogStreamingException.class,
                () -> appender.updateSegment(instanceId, 1, new LogSegmentStats(LogSegmentStatus.OK, 0, 0)));
        assertThrows(LogStreamingException.class, () -> appender.appendLog(instanceId, 2, new byte[]{1}));
        assertEquals(1, requests.size());
    }

    @Test
    void badSegmentsDoNotStopTheTail() {
        for (var code : new int[]{400, 404}) {
            requests.clear();
            responseCode.set(code);
            var consumer = new SegmentedLogsConsumer(instanceId, appender);
            assertDoesNotThrow(() -> consumer.accept(segments(1, 2)));
            assertEquals(4, requests.size()); // content and stats for both segments
        }
    }

    @Test
    void plainLogsStopOnAuthorizationFailureButDiagnosticsRemainBestEffort() {
        responseCode.set(401);
        assertThrows(LogStreamingException.class, () -> appender.appendLog(instanceId, new byte[]{1}));
        var diagnostics = new RemoteProcessLog(instanceId, appender);
        assertDoesNotThrow(() -> diagnostics.info("Process finished with: {}", 0));
        assertEquals(1, requests.size());
    }

    private static RedirectedProcessLog.Chunk segments(long... ids) {
        var bytes = new java.io.ByteArrayOutputStream();
        for (var id : ids) {
            var header = LogSegmentHeader.builder().segmentId(id).status(LogSegmentStatus.RUNNING)
                    .errorCount(0).warnCount(0).length(1).build();
            bytes.writeBytes(LogSegmentSerializer.serializeHeader(header, 1));
            bytes.write('x');
            bytes.writeBytes(LogSegmentSerializer.serializeHeader(LogSegmentHeader.builder()
                    .segmentId(id).status(LogSegmentStatus.OK).errorCount(0).warnCount(0).length(0).build()));
        }
        var ab = bytes.toByteArray();
        return new RedirectedProcessLog.Chunk(ab, ab.length);
    }
}
