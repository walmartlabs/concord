package com.walmartlabs.concord.server.queueclient;

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

import com.walmartlabs.concord.server.queueclient.message.CommandRequest;
import com.walmartlabs.concord.server.queueclient.message.CommandResponse;
import com.walmartlabs.concord.server.queueclient.message.Message;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.server.WebSocketUpgradeHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs {@link QueueClient} against a Jetty websocket endpoint set up the same
 * way as the server's {@code AgentWebSocketConfigurator}.
 */
public class QueueClientTest {

    private Server server;
    private int port;

    private final Map<String, String> upgradeHeaders = new ConcurrentHashMap<>();
    private final CountDownLatch pingReceived = new CountDownLatch(1);

    @BeforeEach
    public void setUp() throws Exception {
        server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(0);
        server.addConnector(connector);

        WebSocketUpgradeHandler handler = WebSocketUpgradeHandler.from(server, container -> {
            container.setIdleTimeout(Duration.ofMinutes(1));
            container.addMapping("/websocket", (req, resp, callback) -> {
                for (String h : new String[]{"Authorization", QueueClient.AGENT_ID, QueueClient.AGENT_UA}) {
                    String v = req.getHeaders().get(h);
                    if (v != null) {
                        upgradeHeaders.put(h, v);
                    }
                }
                return new ServerListener();
            });
        });
        server.setHandler(handler);
        server.start();

        port = connector.getLocalPort();
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void testRequestResponseAndPing() throws Exception {
        QueueClientConfiguration cfg = new QueueClientConfiguration.Builder(new String[]{"ws://localhost:" + port + "/websocket"})
                .agentId("test-agent")
                .apiKey("test-key")
                .userAgent("test-ua")
                .connectTimeout(5000)
                .pingInterval(100)
                .maxNoActivityPeriod(30000)
                .processRequestDelay(10)
                .reconnectDelay(100)
                .build();

        QueueClient client = new QueueClient(cfg);
        client.start();
        try {
            UUID agentId = UUID.randomUUID();
            Message response = client.<Message>request(new CommandRequest(agentId)).get(10, TimeUnit.SECONDS);

            assertInstanceOf(CommandResponse.class, response);
            assertEquals(agentId.toString(), ((CommandResponse) response).getPayload().get("agentId"));

            assertEquals("test-key", upgradeHeaders.get("Authorization"));
            assertEquals("test-agent", upgradeHeaders.get(QueueClient.AGENT_ID));
            assertEquals("test-ua", upgradeHeaders.get(QueueClient.AGENT_UA));

            assertTrue(pingReceived.await(10, TimeUnit.SECONDS));
        } finally {
            client.stop();
        }
    }

    public class ServerListener implements Session.Listener.AutoDemanding {

        private Session session;

        @Override
        public void onWebSocketOpen(Session session) {
            this.session = session;
        }

        @Override
        public void onWebSocketText(String message) {
            CommandRequest request = (CommandRequest) MessageSerializer.deserialize(message);
            CommandResponse response = CommandResponse.cancel(request.getCorrelationId(), Map.of("agentId", request.getAgentId().toString()));
            session.sendText(MessageSerializer.serialize(response), Callback.NOOP);
        }

        @Override
        public void onWebSocketPing(ByteBuffer payload) {
            pingReceived.countDown();
            session.sendPong(payload, Callback.NOOP);
        }
    }
}
