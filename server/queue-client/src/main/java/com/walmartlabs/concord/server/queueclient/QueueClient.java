package com.walmartlabs.concord.server.queueclient;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.google.common.util.concurrent.SettableFuture;
import com.walmartlabs.concord.server.queueclient.message.Message;
import com.walmartlabs.concord.server.queueclient.message.MessageType;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.ee8.websocket.api.Session;
import org.eclipse.jetty.ee8.websocket.api.WebSocketListener;
import org.eclipse.jetty.ee8.websocket.api.WebSocketPingPongListener;
import org.eclipse.jetty.ee8.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.ee8.websocket.client.WebSocketClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.HttpHeaders;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class QueueClient {

    public static final String AGENT_UA = "X-Concord-Agent";
    public static final String AGENT_ID = "X-Concord-Agent-Id";

    private static final Logger log = LoggerFactory.getLogger(QueueClient.class);

    private final Set<MessageType> ignoreRequests;
    private final List<RequestEntry> requests;

    private final Worker worker;
    private Thread workerThread;
    private boolean onMaintenanceMode;

    public QueueClient(QueueClientConfiguration cfg) throws URISyntaxException {
        this.ignoreRequests = new HashSet<>();
        this.requests = new ArrayList<>();

        this.worker = new Worker(cfg, requests);
    }

    public void start() {
        this.workerThread = new Thread(worker, "queue-client");
        this.workerThread.start();
    }

    public void stop() {
        if (workerThread == null) {
            return;
        }

        workerThread.interrupt();
        workerThread = null;
    }

    public void maintenanceMode() {
        if (workerThread == null) {
            return;
        }

        synchronized (requests) {
            if (onMaintenanceMode) {
                return;
            }
            ignoreRequests.add(MessageType.PROCESS_REQUEST);
            worker.disconnect();
            onMaintenanceMode = true;
        }
    }

    @SuppressWarnings("unchecked")
    public <E extends Message> Future<E> request(Message request) {
        SettableFuture<Message> f = SettableFuture.create();
        synchronized (requests) {
            if (ignoreRequests.contains(request.getMessageType())) {
                f.set(null);
            } else {
                requests.add(new RequestEntry(request, f));
            }
        }
        return (Future<E>) f;
    }

    private static final class Worker implements Runnable, WebSocketListener, WebSocketPingPongListener {

        private enum State {
            CONNECTING,
            CONNECTED,
            DISCONNECTING
        }

        private final AtomicLong requestIdGenerator = new AtomicLong();

        private final String agentId;
        private final String userAgent;
        private final String apiToken;
        private final URI[] destUris;
        private final Map<Long, RequestEntry> awaitResponses;
        private final List<RequestEntry> requests;
        private final long pingInterval;
        private final long maxNoActivityPeriod;
        private final long connectTimeout;

        private final long processRequestDelay;
        private final long reconnectDelay;

        private WebSocketClient client;
        private CompletableFuture<Void> closeFuture;

        private long lastRequestTimestamp;
        private long lastResponseTimestamp;

        private final AtomicReference<State> state;

        public Worker(QueueClientConfiguration cfg, List<RequestEntry> requests) throws URISyntaxException {
            this.agentId = cfg.getAgentId();
            this.userAgent = cfg.getUserAgent();
            this.apiToken = cfg.getApiKey();

            this.requests = requests;
            this.destUris = toURIs(cfg.getAddresses());
            this.awaitResponses = new ConcurrentHashMap<>();
            this.pingInterval = cfg.getPingInterval();
            this.maxNoActivityPeriod = cfg.getMaxNoActivityPeriod();
            this.connectTimeout = cfg.getConnectTimeout();

            this.processRequestDelay = cfg.getProcessRequestDelay();
            this.reconnectDelay = cfg.getReconnectDelay();

            this.state = new AtomicReference<>(State.CONNECTING);
        }

        private void mainLoop() {
            int destUriIndex = 0;

            Session session = null;
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    switch (state.get()) {
                        case CONNECTING: {
                            session = connect(destUris[destUriIndex]);
                            state.set(State.CONNECTED);
                            lastRequestTimestamp = System.currentTimeMillis();
                            lastResponseTimestamp = lastRequestTimestamp;
                            log.info("connect ['{}'] -> done", destUris[destUriIndex]);
                            break;
                        }
                        case CONNECTED: {
                            processRequests(session);
                            processPing(session);
                            sleep(processRequestDelay);
                            break;
                        }
                        case DISCONNECTING: {
                            close(session);
                            session = null;
                            state.set(State.CONNECTING);
                            sleep(reconnectDelay);
                            break;
                        }
                    }
                } catch (Exception e) {
                    log.error("mainLoop -> error", e);
                    state.set(State.DISCONNECTING);

                    if (++destUriIndex >= destUris.length) {
                        destUriIndex = 0;
                    }
                }
            }

            if (session != null) {
                close(session);
            }
        }

        @Override
        public void run() {
            mainLoop();

            log.info("run -> finished");
        }

        @Override
        public void onWebSocketPong(ByteBuffer payload) {
            this.lastResponseTimestamp = System.currentTimeMillis();
        }

        @Override
        public void onWebSocketPing(ByteBuffer payload) {
            // we don't expect pings
        }

        @Override
        public void onWebSocketBinary(byte[] payload, int offset, int len) {
            log.warn("onWebSocketBinary [{}, '{}'] -> ignored", offset, len);
        }

        @Override
        public void onWebSocketText(String message) {
            this.lastResponseTimestamp = System.currentTimeMillis();

            Message response = MessageSerializer.deserialize(message);
            RequestEntry request = awaitResponses.remove(response.getCorrelationId());
            if (request == null) {
                log.error("onWebSocketText ['{}'] -> request not found", message);
                return;
            }
            request.onResponse(response);
            log.debug("onWebSocketText ['{}'] -> done", message);
        }

        @Override
        public void onWebSocketClose(int statusCode, String reason) {
            state.set(State.DISCONNECTING);
            closeFuture.complete(null);
            log.info("onWebSocketClose [{}, '{}'] -> done", statusCode, reason);
        }

        @Override
        public void onWebSocketConnect(Session session) {
            log.info("onWebSocketConnect ['{}'] -> done", session);
        }

        @Override
        public void onWebSocketError(Throwable cause) {
            if (cause instanceof ClosedChannelException) {
                log.warn("onWebSocketError -> closed channel");
            } else {
                log.error("onWebSocketError -> '{}'", cause.getMessage());
            }
        }

        public void disconnect() {
            state.set(State.DISCONNECTING);
        }

        private Session connect(URI destUri) throws Exception {
            this.client = createWebSocketClient(connectTimeout);
            this.client.start();

            closeFuture = new CompletableFuture<>();

            ClientUpgradeRequest request = new ClientUpgradeRequest();
            request.setHeader(AGENT_ID, agentId);
            request.setHeader(AGENT_UA, userAgent);
            request.setHeader(HttpHeaders.AUTHORIZATION, apiToken);
            return client.connect(this, destUri, request).get(connectTimeout, TimeUnit.MILLISECONDS);
        }

        private boolean send(Session session, Message message) {
            if (!session.isOpen()) {
                return false;
            }

            message.setCorrelationId(requestIdGenerator.incrementAndGet());
            try {
                session.getRemote().sendString(MessageSerializer.serialize(message));

                lastRequestTimestamp = System.currentTimeMillis();

                log.info("send ['{}'] -> done", message);
                return true;
            } catch (Exception e) {
                log.error("send ['{}'] -> error", message, e);
                return false;
            }
        }

        private void processRequests(Session session) {
            RequestEntry e = nextRequest();
            if (e == null) {
                return;
            }

            boolean sent = send(session, e.request);
            if (!sent) {
                e.cancel();
                state.set(State.DISCONNECTING);
                return;
            }

            awaitResponses.put(e.getCorrelationId(), e);
        }

        private void processPing(Session session) throws IOException {
            long now = System.currentTimeMillis();

            if (now - lastRequestTimestamp >= pingInterval) {
                session.getRemote().sendPing(ByteBuffer.wrap("ping".getBytes()));
            }

            if (now - lastResponseTimestamp >= maxNoActivityPeriod) {
                log.warn("processPing -> no response for more than {}ms...", maxNoActivityPeriod);
                disconnect();
            }
        }

        private void close(Session session) {
            try {
                if (session != null && session.isOpen()) {
                    session.close();

                    closeFuture.get(5, TimeUnit.SECONDS);
                    closeFuture = null;
                }
            } catch (TimeoutException e) {
                log.warn("close -> error: timeout waiting for close");
            } catch (Exception e) {
                log.warn("close -> error", e);
            }

            try {
                this.client.stop();
                this.client.destroy();
            } catch (InterruptedException e) {
                // ignore, we're stopping anyway
            } catch (Exception e) {
                log.warn("stop -> error: {}", e.getMessage());
            }

            synchronized (this.requests) {
                this.requests.forEach(RequestEntry::cancel);
                this.requests.clear();

                this.awaitResponses.values().forEach(RequestEntry::cancel);
                this.awaitResponses.clear();
            }

            log.info("close -> done");
        }

        private RequestEntry nextRequest() {
            synchronized (requests) {
                Iterator<RequestEntry> it = requests.iterator();
                while (it.hasNext()) {
                    RequestEntry request = it.next();
                    if (!alreadySent(request.request.getMessageType())) {
                        it.remove();
                        return request;
                    }
                }
            }

            return null;
        }

        private boolean alreadySent(MessageType requestType) {
            return awaitResponses.values().stream()
                    .anyMatch(ar -> ar.request.getMessageType() == requestType);
        }

        private void sleep(long ms) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private static URI[] toURIs(String[] as) throws URISyntaxException {
            URI[] result = new URI[as.length];
            for (int i = 0; i < as.length; i++) {
                result[i] = new URI(as[i]);
            }
            return result;
        }
    }

    private static class RequestEntry {

        private final Message request;
        private final SettableFuture<Message> future;

        public RequestEntry(Message request, SettableFuture<Message> future) {
            this.request = request;
            this.future = future;
        }

        public void onResponse(Message response) {
            future.set(response);
        }

        public void cancel() {
            future.set(null);
        }

        public Long getCorrelationId() {
            return request.getCorrelationId();
        }
    }

    private static WebSocketClient createWebSocketClient(long connectTimeout) {
        SslContextFactory.Client scf = new SslContextFactory.Client();
        scf.setValidateCerts(false);
        scf.setValidatePeerCerts(false);
        scf.setTrustAll(true);

        HttpClient httpClient = new HttpClient();
        httpClient.setSslContextFactory(scf);

        WebSocketClient c = new WebSocketClient(httpClient);
        c.setStopAtShutdown(false);
        c.setConnectTimeout(connectTimeout);
        return c;
    }
}
