package com.walmartlabs.concord.server.process.queue.dispatcher;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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

import com.walmartlabs.concord.server.metrics.ProcessQueueMetrics;
import com.codahale.metrics.MetricRegistry;
import com.walmartlabs.concord.imports.Imports;
import com.walmartlabs.concord.process.loader.ImportsNormalizer;
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.Locks;
import com.walmartlabs.concord.server.cfg.ProcessQueueConfiguration;
import com.walmartlabs.concord.server.message.MessageChannel;
import com.walmartlabs.concord.server.message.MessageChannelManager;
import com.walmartlabs.concord.server.process.ImportsNormalizerFactory;
import com.walmartlabs.concord.server.process.SessionTokenCreator;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import com.walmartlabs.concord.server.process.queue.ProcessQueueEntry;
import com.walmartlabs.concord.server.process.queue.ProcessQueueManager;
import com.walmartlabs.concord.server.queueclient.message.Message;
import com.walmartlabs.concord.server.queueclient.message.MessageType;
import com.walmartlabs.concord.server.queueclient.message.ProcessRequest;
import com.walmartlabs.concord.server.queueclient.message.ProcessResponse;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Set;
import static com.walmartlabs.concord.server.metrics.ProcessQueueMetrics.*;
import static org.mockito.Mockito.doThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static java.time.temporal.ChronoUnit.MICROS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DispatcherTest {

    private static List<Dispatcher.Request> requests;

    @Mock
    private Dispatcher.RequirementsMatcherErrorHandler errHandler;

    @Mock
    private DSLContext tx;

    @Mock
    private Locks locks;

    @Mock
    private ProcessQueueManager queueManager;

    @Mock
    private ProcessLogManager logManager;

    @Mock
    private ImportsNormalizerFactory importsNormalizerFactory;

    @Mock
    private SessionTokenCreator sessionTokenCreator;

    @Mock
    private ProcessQueueConfiguration cfg;

    @BeforeAll
    static void setup() {
        requests = List.of(new Dispatcher.Request(null, processRequest(0)));
    }

    @Test
    void testInvalidInvalidRegex() {
        var candidate = generateCandidate("*invalidRegex*");
        assertNull(Dispatcher.findRequest(candidate, requests, tx, errHandler));
        verify(errHandler, times(1)).handleError(any(), any(), any());
    }

    @Test
    void testValid() {
        var candidate = generateCandidate(".*default.*");
        assertNotNull(Dispatcher.findRequest(candidate, requests, tx, errHandler));
        verify(errHandler, times(0)).handleError(any(), any(), any());
    }

    @Test
    void testResponseIncludesRequirementsInProcessResponse() {
        UUID projectId = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.now().truncatedTo(MICROS);
        Map<String, Object> requirements = Map.of(
                "agent", Map.of(
                        "flavor", List.of(".*default.*")
                ),
                "custom", Map.of(
                        "foo", "bar"
                ));
        Imports imports = Imports.builder().build();
        ProcessQueueEntry candidate = ProcessQueueEntry.builder()
                .key(new ProcessKey(UUID.randomUUID(), createdAt))
                .projectId(projectId)
                .repoUrl("repo-url")
                .repoPath("repo-path")
                .commitId("commit-id")
                .commitBranch("repo-branch")
                .imports(imports)
                .requirements(requirements)
                .build();

        TestDispatcherDao dao = new TestDispatcherDao(tx, candidate);
        MessageChannelManager channelManager = new MessageChannelManager(new ProcessQueueMetrics(new com.codahale.metrics.MetricRegistry()));
        TestMessageChannel channel = new TestMessageChannel("channel-1", "agent-1", processRequest(321));
        channelManager.add(channel);

        ImportsNormalizer normalizer = value -> value;
        when(importsNormalizerFactory.forProject(projectId)).thenReturn(normalizer);
        when(sessionTokenCreator.create(candidate.key())).thenReturn("session-token");
        when(cfg.getDispatcherPollDelay()).thenReturn(Duration.ofSeconds(1));
        when(cfg.getDispatcherBatchSize()).thenReturn(10);

        Dispatcher dispatcher = new Dispatcher(locks, dao, channelManager, logManager, queueManager, Collections.emptySet(),
                importsNormalizerFactory, cfg, new MetricRegistry(), new ProcessQueueMetrics(new MetricRegistry()), sessionTokenCreator);

        assertTrue(dispatcher.performTask());

        verify(queueManager, times(1)).updateAgentId(eq(tx), eq(candidate.key()), eq("agent-1"), eq(ProcessStatus.STARTING));

        Message response = channel.response();
        ProcessResponse processResponse = assertInstanceOf(ProcessResponse.class, response);
        assertEquals(requirements, processResponse.getRequirements());
        assertEquals("session-token", processResponse.getSessionToken());
        assertEquals(imports, processResponse.getImports());
    }

    @Test
    void testPassWithoutCredits() {
        MetricRegistry registry = new MetricRegistry();
        Dispatcher dispatcher = metricDispatcher(registry, new MessageChannelManager(new ProcessQueueMetrics(registry)), Set.of());
        assertFalse(dispatcher.performTask());
        assertEquals(1, registry.counter("process-queue-dispatcher-pass-no-credits").getCount());
        assertEquals(0, registry.histogram("process-queue-dispatcher-credits-per-pass").getSnapshot().getMax());
        assertEquals(0, registry.timer("process-queue-dispatcher-lock-wait").getCount());
    }

    @Test
    void testPassWithoutCandidates() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        assertFalse(metricDispatcher(registry, credits(registry, true), Set.of()).performTask());
        assertEquals(1, registry.counter("process-queue-dispatcher-pass-no-candidates").getCount());
        assertEquals(1, registry.histogram("process-queue-dispatcher-pages-per-pass").getSnapshot().getMax());
    }

    @Test
    void testNoCreditMatchReadsMultiplePages() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        assertFalse(metricDispatcher(registry, credits(registry, true), Set.of(), generateCandidate("other")).performTask());
        assertEquals(1, registry.counter("process-queue-dispatcher-pass-no-match").getCount());
        assertEquals(1, registry.counter("process-queue-dispatcher-rejection-no-credit-match").getCount());
        assertEquals(1, registry.histogram("process-queue-dispatcher-candidates-per-pass").getSnapshot().getMax());
        assertEquals(2, registry.histogram("process-queue-dispatcher-pages-per-pass").getSnapshot().getMax());
    }

    @Test
    void testCustomFilterRejection() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        Filter custom = mock(Filter.class);
        assertFalse(metricDispatcher(registry, credits(registry, true), Set.of(custom), generateCandidate("default")).performTask());
        assertEquals(1, registry.counter("process-queue-dispatcher-rejection-filter-other").getCount());
        assertEquals(1, registry.counter("process-queue-dispatcher-pass-no-match").getCount());
        verify(custom).cleanup();
    }

    @Test
    void testBuiltInFilterReasonsAndCustomSubclass() {
        assertEquals(Rejection.FILTER_CONCURRENT, Dispatcher.rejection(new ConcurrentProcessFilter(null, null, null, null)));
        assertEquals(Rejection.FILTER_EXCLUSIVE, Dispatcher.rejection(new ExclusiveProcessFilter(null, null, null)));
        assertEquals(Rejection.FILTER_OTHER, Dispatcher.rejection(new ExclusiveProcessFilter(null, null, null) {}));
    }

    @Test
    void testSentResponseAndScheduledAgeClamping() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        ProcessQueueEntry candidate = ProcessQueueEntry.builder().key(ProcessKey.random())
                .lastUpdatedAt(OffsetDateTime.now().minusDays(1)).startAt(OffsetDateTime.now().plusDays(1)).build();
        when(importsNormalizerFactory.forProject(null)).thenReturn(value -> value);
        assertTrue(metricDispatcher(registry, credits(registry, true), Set.of(), candidate).performTask());
        assertEquals(1, registry.counter("process-queue-dispatcher-pass-matched").getCount());
        assertEquals(1, registry.counter("process-queue-dispatcher-response-sent").getCount());
        assertEquals(0, registry.histogram("process-queue-dispatcher-enqueued-age-ms").getSnapshot().getMax());
        assertEquals(1, registry.timer("process-queue-dispatcher-lock-wait").getCount());
        assertEquals(1, registry.timer("process-queue-dispatcher-lock-hold").getCount());
    }

    @Test
    void testSendFailure() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        when(importsNormalizerFactory.forProject(null)).thenReturn(value -> value);
        assertTrue(metricDispatcher(registry, credits(registry, false), Set.of(), generateCandidate("default")).performTask());
        assertEquals(1, registry.counter("process-queue-dispatcher-response-send-failed").getCount());
        assertEquals(0, registry.counter("process-queue-dispatcher-response-prepare-failed").getCount());
    }

    @Test
    void testPrepareFailure() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        when(importsNormalizerFactory.forProject(null)).thenThrow(new IllegalStateException("prepare"));
        assertTrue(metricDispatcher(registry, credits(registry, true), Set.of(), generateCandidate("default")).performTask());
        assertEquals(1, registry.counter("process-queue-dispatcher-response-prepare-failed").getCount());
        assertEquals(0, registry.counter("process-queue-dispatcher-response-sent").getCount());
    }

    @Test
    void testLoggingFailureDoesNotDoubleCountResponse() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        ProcessQueueMetrics metrics = new ProcessQueueMetrics(registry);
        MessageChannelManager manager = new MessageChannelManager(metrics);
        com.walmartlabs.concord.server.agent.websocket.WebSocketChannel channel = mock(com.walmartlabs.concord.server.agent.websocket.WebSocketChannel.class);
        when(channel.getChannelId()).thenReturn("channel");
        when(channel.getMessage(MessageType.PROCESS_REQUEST)).thenReturn(Optional.of(processRequest(1)));
        when(channel.requestArrivalNanos(1)).thenReturn(java.util.OptionalLong.of(System.nanoTime() - 10_000_000));
        when(channel.offerMessage(any())).thenReturn(true);
        when(channel.getUserAgent()).thenReturn("ua");
        manager.add(channel);
        when(importsNormalizerFactory.forProject(null)).thenReturn(value -> value);
        doThrow(new IllegalStateException("log")).when(logManager).info(any(), any());
        assertTrue(metricDispatcher(registry, manager, Set.of(), generateCandidate("default")).performTask());
        assertEquals(1, registry.counter("process-queue-dispatcher-response-sent").getCount());
        assertEquals(0, registry.counter("process-queue-dispatcher-response-prepare-failed").getCount());
        assertEquals(1, registry.histogram("process-queue-dispatcher-credit-age-ms").getCount());
    }

    private Dispatcher metricDispatcher(MetricRegistry registry, MessageChannelManager manager, Set<Filter> filters, ProcessQueueEntry... candidates) {
        when(cfg.getDispatcherPollDelay()).thenReturn(Duration.ofSeconds(1));
        when(cfg.getDispatcherBatchSize()).thenReturn(10);
        return new Dispatcher(locks, new TestDispatcherDao(tx, candidates), manager, logManager, queueManager, filters,
                importsNormalizerFactory, cfg, registry, new ProcessQueueMetrics(registry), sessionTokenCreator);
    }

    private static MessageChannelManager credits(MetricRegistry registry, boolean canSend) throws Exception {
        MessageChannelManager manager = new MessageChannelManager(new ProcessQueueMetrics(registry));
        MessageChannel channel = mock(MessageChannel.class);
        when(channel.getChannelId()).thenReturn("channel");
        when(channel.getMessage(MessageType.PROCESS_REQUEST)).thenReturn(Optional.of(processRequest(1)));
        // lenient: no-match passes never attempt to send a response.
        org.mockito.Mockito.lenient().when(channel.offerMessage(any())).thenReturn(canSend);
        manager.add(channel);
        return manager;
    }

    private static ProcessQueueEntry generateCandidate(String flavor) {
        return ProcessQueueEntry.builder()
                .requirements(Map.of(
                        "agent", Map.of(
                                "flavor", List.of(flavor)
                        )
                ))
                .key(ProcessKey.random())
                .build();
    }

    private static ProcessRequest processRequest(long correlationId) {
        ProcessRequest request = new ProcessRequest(Map.of("flavor", "default"));
        request.setCorrelationId(correlationId);
        return request;
    }

    private static class TestDispatcherDao extends Dispatcher.DispatcherDao {

        private final DSLContext tx;
        private final List<ProcessQueueEntry> candidates;
        private boolean fetched;

        private TestDispatcherDao(DSLContext tx, ProcessQueueEntry... candidates) {
            super(mock(Configuration.class), mock(ConcordObjectMapper.class), new MetricRegistry());
            this.tx = tx;
            this.candidates = List.of(candidates);
        }

        @Override
        protected <T> T txResult(TxResult<T> t) {
            try {
                return t.run(tx);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public List<ProcessQueueEntry> next(DSLContext tx, int offset, int limit) {
            if (fetched) {
                return List.of();
            }

            fetched = true;
            return candidates;
        }
    }

    private static class TestMessageChannel implements MessageChannel {

        private final String channelId;
        private final String agentId;
        private ProcessRequest request;
        private Message response;

        private TestMessageChannel(String channelId, String agentId, ProcessRequest request) {
            this.channelId = channelId;
            this.agentId = agentId;
            this.request = request;
        }

        @Override
        public String getChannelId() {
            return channelId;
        }

        @Override
        public String getAgentId() {
            return agentId;
        }

        @Override
        public boolean offerMessage(Message msg) {
            this.response = msg;
            return true;
        }

        @Override
        public Optional<Message> getMessage(MessageType messageType) {
            if (request != null && request.getMessageType() == messageType) {
                ProcessRequest current = request;
                request = null;
                return Optional.of(current);
            }

            return Optional.empty();
        }

        @Override
        public void close() {
        }

        private Message response() {
            return response;
        }
    }
}
