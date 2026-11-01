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
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises request bookkeeping without starting a network connection or worker thread. */
class QueueClientRequestTest {

    private QueueClient client;
    private QueueClient.Worker worker;

    @BeforeEach
    void setUp() throws Exception {
        var cfg = new QueueClientConfiguration.Builder(new String[]{"ws://localhost/websocket"}).build();
        client = new QueueClient(cfg);
        worker = (QueueClient.Worker) field(client, "worker");
    }

    @Test
    void testResponseBeforeSendCompletion() throws Exception {
        var agentId = UUID.randomUUID();
        var request = new CommandRequest(agentId);
        var future = client.<Message>request(request);
        var session = session(true, (text, callback) -> {
            var sentRequest = (CommandRequest) MessageSerializer.deserialize(text);
            var response = CommandResponse.cancel(sentRequest.getCorrelationId(),
                    Map.of("agentId", sentRequest.getAgentId().toString()));

            // Deliver the response before acknowledging the send, deterministically reproducing the race.
            worker.onWebSocketText(MessageSerializer.serialize(response));
            callback.succeed();
        });

        processRequests(session);

        assertTrue(future.isDone(), "A response arriving before send completion must not be lost");
        var response = assertInstanceOf(CommandResponse.class, future.get());
        assertEquals(request.getCorrelationId(), response.getCorrelationId());
        assertEquals(agentId.toString(), response.getPayload().get("agentId"));
        assertNoPendingRequests();
    }

    @Test
    void testFailedSendRemovesPendingRequest() throws Exception {
        var future = client.<Message>request(new CommandRequest(UUID.randomUUID()));
        var session = session(true, (text, callback) -> callback.fail(new IOException("Send failed")));

        processRequests(session);

        assertSendFailed(future);
    }

    @Test
    void testClosedSessionRemovesPendingRequest() throws Exception {
        var future = client.<Message>request(new CommandRequest(UUID.randomUUID()));
        var session = session(false, (text, callback) -> fail("Must not send on a closed session"));

        processRequests(session);

        assertSendFailed(future);
    }

    private void assertSendFailed(Future<Message> future) throws Exception {
        assertTrue(future.isDone(), "A failed send must complete the caller's future");
        assertNull(future.get());
        assertNoPendingRequests();
        assertEquals("DISCONNECTING", field(worker, "state").toString());
    }

    private void assertNoPendingRequests() throws Exception {
        assertTrue(((Map<?, ?>) field(worker, "awaitResponses")).isEmpty(),
                "Completed requests must not block subsequent requests of the same type");
    }

    private void processRequests(Session session) throws Exception {
        // Run a single worker iteration without connection setup, sleeps or scheduling races.
        var method = QueueClient.Worker.class.getDeclaredMethod("processRequests", Session.class);
        method.setAccessible(true);
        method.invoke(worker, session);
    }

    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Session session(boolean open, BiConsumer<String, Callback> onSend) {
        return (Session) Proxy.newProxyInstance(Session.class.getClassLoader(), new Class<?>[]{Session.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "isOpen":
                            return open;
                        case "sendText":
                            onSend.accept((String) args[0], (Callback) args[1]);
                            return null;
                        default:
                            throw new AssertionError("Unexpected session call: " + method.getName());
                    }
                });
    }
}
