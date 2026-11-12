package com.walmartlabs.concord.server.agent.websocket;

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

import com.walmartlabs.concord.server.message.MessageChannel;
import com.walmartlabs.concord.server.message.MessageChannelManager;
import com.walmartlabs.concord.server.queueclient.QueueClient;
import com.walmartlabs.concord.server.queueclient.QueueClientConfiguration;
import com.walmartlabs.concord.server.queueclient.message.CommandRequest;
import com.walmartlabs.concord.server.queueclient.message.CommandResponse;
import com.walmartlabs.concord.server.queueclient.message.Message;
import com.walmartlabs.concord.server.queueclient.message.MessageType;
import com.walmartlabs.concord.server.security.Permission;
import com.walmartlabs.concord.server.security.UserSecurityContext;
import com.walmartlabs.concord.server.security.apikey.ApiKeyDao;
import com.walmartlabs.concord.server.security.apikey.ApiKeyEntry;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.exceptions.UpgradeException;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.jetty.websocket.server.WebSocketUpgradeHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Runs the agent websocket endpoint the same way as {@code HttpServer} does:
 * a core {@link WebSocketUpgradeHandler} in front of the rest of the handlers.
 */
public class AgentWebSocketTest {

    private static final String API_KEY = Base64.getEncoder().encodeToString("test-key".getBytes(StandardCharsets.UTF_8));
    private static final String UNKNOWN_API_KEY = Base64.getEncoder().encodeToString("unknown".getBytes(StandardCharsets.UTF_8));
    private static final UUID USER_ID = UUID.randomUUID();

    private final MessageChannelManager channelManager = new MessageChannelManager();
    private final WebsocketsConfiguration cfg = mock(WebsocketsConfiguration.class);
    private final UserSecurityContext userSecurityContext = mock(UserSecurityContext.class);

    private Server server;
    private WebSocketClient client;
    private URI uri;

    @BeforeEach
    public void setUp() throws Exception {
        ApiKeyDao apiKeyDao = mock(ApiKeyDao.class);
        when(apiKeyDao.find(API_KEY)).thenReturn(new ApiKeyEntry(UUID.randomUUID(), USER_ID, "test", null));

        AgentWebSocketConfigurator configurator = new AgentWebSocketConfigurator(channelManager, apiKeyDao, cfg, userSecurityContext);

        server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(0);
        server.addConnector(connector);

        WebSocketUpgradeHandler handler = WebSocketUpgradeHandler.from(server, configurator::configure);
        // non-upgrade requests go to the next handler
        handler.setHandler(new Handler.Abstract() {
            @Override
            public boolean handle(org.eclipse.jetty.server.Request request, org.eclipse.jetty.server.Response response, org.eclipse.jetty.util.Callback callback) {
                response.setStatus(418);
                callback.succeeded();
                return true;
            }
        });
        server.setHandler(handler);
        server.start();

        uri = URI.create("ws://localhost:" + connector.getLocalPort() + AgentWebSocketConfigurator.PATH);

        client = new WebSocketClient();
        client.start();
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (client != null) {
            client.stop();
        }
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void testRequestResponse() throws Exception {
        QueueClientConfiguration clientCfg = new QueueClientConfiguration.Builder(new String[]{uri.toString()})
                .agentId("test-agent")
                .apiKey(API_KEY)
                .userAgent("test-ua")
                .connectTimeout(5000)
                .pingInterval(100)
                .maxNoActivityPeriod(30000)
                .processRequestDelay(10)
                .reconnectDelay(100)
                .build();

        QueueClient queueClient = new QueueClient(clientCfg);
        queueClient.start();
        try {
            UUID agentId = UUID.randomUUID();
            var future = queueClient.<Message>request(new CommandRequest(agentId));

            Map.Entry<MessageChannel, CommandRequest> request = awaitRequest();
            WebSocketChannel channel = (WebSocketChannel) request.getKey();
            assertEquals("test-agent", channel.getAgentId());
            assertEquals("test-ua", channel.getUserAgent());
            assertEquals(agentId, request.getValue().getAgentId());

            assertTrue(channelManager.sendMessage(channel.getChannelId(),
                    CommandResponse.cancel(request.getValue().getCorrelationId(), Map.of("agentId", agentId.toString()))));

            Message response = future.get(10, TimeUnit.SECONDS);
            assertInstanceOf(CommandResponse.class, response);
            assertEquals(agentId.toString(), ((CommandResponse) response).getPayload().get("agentId"));
        } finally {
            queueClient.stop();
        }
    }

    @Test
    public void testPing() throws Exception {
        TestListener listener = new TestListener();
        Session session = connect(API_KEY, listener).get(10, TimeUnit.SECONDS);

        try {
            Callback.Completable.with(c -> session.sendPing(ByteBuffer.wrap("ping".getBytes()), c)).get();
            assertEquals("pong", listener.pong.get(10, TimeUnit.SECONDS));
        } finally {
            session.close();
        }
    }

    @Test
    public void testMissingApiKey() {
        assertUpgradeStatus(401, null);
    }

    @Test
    public void testMalformedApiKey() {
        assertUpgradeStatus(403, "not base64!");
    }

    @Test
    public void testUnknownApiKey() {
        assertUpgradeStatus(403, UNKNOWN_API_KEY);
    }

    @Test
    public void testPermissionRequired() {
        when(cfg.isRequirePermission()).thenReturn(true);
        when(userSecurityContext.isPermitted(any(), eq(Permission.AGENT_WEBSOCKET))).thenReturn(false);
        assertUpgradeStatus(403, API_KEY);
    }

    @Test
    public void testMaintenanceMode() {
        channelManager.shutdown();
        assertUpgradeStatus(503, API_KEY);
    }

    @Test
    public void testNonUpgradeRequest() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(uri.toString().replace("ws://", "http://")).toURL().openConnection();
        try {
            assertEquals(418, conn.getResponseCode());
        } finally {
            conn.disconnect();
        }
    }

    private void assertUpgradeStatus(int expectedStatus, String apiKey) {
        ExecutionException e = assertThrows(ExecutionException.class,
                () -> connect(apiKey, new TestListener()).get(10, TimeUnit.SECONDS));

        UpgradeException upgradeException = assertInstanceOf(UpgradeException.class, e.getCause());
        assertEquals(expectedStatus, upgradeException.getResponseStatusCode());
    }

    private CompletableFuture<Session> connect(String apiKey, Session.Listener listener) throws Exception {
        ClientUpgradeRequest req = new ClientUpgradeRequest();
        if (apiKey != null) {
            req.setHeader("Authorization", apiKey);
        }
        req.setHeader(QueueClient.AGENT_ID, "test-agent");
        return client.connect(listener, uri, req);
    }

    @SuppressWarnings("unchecked")
    private Map.Entry<MessageChannel, CommandRequest> awaitRequest() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            Map<MessageChannel, CommandRequest> requests = channelManager.getRequests(MessageType.COMMAND_REQUEST);
            if (!requests.isEmpty()) {
                return requests.entrySet().iterator().next();
            }
            Thread.sleep(50);
        }
        throw new AssertionError("No requests received");
    }

    // must be public, Jetty calls the listener's methods via method handles
    public static class TestListener implements Session.Listener.AutoDemanding {

        private final CompletableFuture<String> pong = new CompletableFuture<>();

        @Override
        public void onWebSocketPong(ByteBuffer payload) {
            pong.complete(StandardCharsets.UTF_8.decode(payload).toString());
        }

        @Override
        public void onWebSocketError(Throwable cause) {
            // upgrade errors are checked via the connect() future
        }
    }
}
