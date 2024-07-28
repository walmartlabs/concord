package com.walmartlabs.concord.agent.mmode;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class MaintenanceModeNotifier {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceModeNotifier.class);

    private final HttpServer server;

    public MaintenanceModeNotifier(String host, Integer port, MaintenanceModeListener listener) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(host, port), 0);
        this.server.createContext("/maintenance-mode", new MaintenanceModeHandler(listener));
    }

    public void start() {
        server.start();
        log.info("start -> done, listening on {}", server.getAddress());
    }

    public void stop() {
        server.stop(0);
        log.info("stop -> done");
    }

    private static class MaintenanceModeHandler implements HttpHandler {

        private static final String NOT_FOUND_RESPONSE = "404 (Not Found)\n";

        private final ObjectMapper objectMapper = new ObjectMapper();
        private final MaintenanceModeListener listener;

        private MaintenanceModeHandler(MaintenanceModeListener listener) {
            this.listener = listener;
        }

        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            MaintenanceModeListener.Status status = null;
            if ("GET".equals(httpExchange.getRequestMethod())) {
                status = onMaintenanceModeStatus();
            } else if ("POST".equals(httpExchange.getRequestMethod())) {
                status = onMaintenanceMode();
            }

            if (status != null) {
                httpExchange.getResponseHeaders().set("Content-Type", "application/json");
                response(httpExchange, 200, objectMapper.writeValueAsBytes(status));
                return;
            }

            response(httpExchange, 404, NOT_FOUND_RESPONSE.getBytes());
        }

        private void response(HttpExchange httpExchange, int code, byte[] response) throws IOException {
            httpExchange.sendResponseHeaders(code, response.length);
            try (OutputStream os = httpExchange.getResponseBody()) {
                os.write(response);
            }
        }

        private MaintenanceModeListener.Status onMaintenanceMode() {
            MaintenanceModeListener.Status status = listener.onMaintenanceMode();

            log.info("onMaintenanceMode -> {}", status);

            return status;
        }

        private MaintenanceModeListener.Status onMaintenanceModeStatus() {
            MaintenanceModeListener.Status status = listener.getMaintenanceModeStatus();

            log.info("onMaintenanceModeStatus -> {}", status);

            return status;
        }
    }
}
