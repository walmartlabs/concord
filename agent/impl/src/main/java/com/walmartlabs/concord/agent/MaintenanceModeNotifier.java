package com.walmartlabs.concord.agent;

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

    public MaintenanceModeNotifier(MaintenanceModeListener listener) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("localhost", 8010), 0);
        this.server.createContext("/maintenance-mode", new MaintenanceModeHandler(listener));
    }

    public void start() {
        server.start();

        log.info("start -> done");
    }

    public void stop() {
        server.stop(0);

        log.info("stop -> done");
    }

    private class MaintenanceModeHandler implements HttpHandler {

        private static final String SUCCESS_RESPONSE = "{\"status\": \"ok\"}";
        private static final String BUSY_RESPONSE = "{\"status\": \"busy\", \"busyWorkers\": %d}";

        private final MaintenanceModeListener listener;

        private MaintenanceModeHandler(MaintenanceModeListener listener) {
            this.listener = listener;
        }

        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            if (!"POST".equals(httpExchange.getRequestMethod())) {
                String response = "404 (Not Found)\n";
                httpExchange.sendResponseHeaders(404, response.length());
                try(OutputStream os = httpExchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                return;
            }

            byte[] response;
            long busyWorkers = listener.onMaintenanceMode();
            if (busyWorkers == 0) {
                response = SUCCESS_RESPONSE.getBytes();
                log.info("handle -> maintenance mode on");
            } else {
                response = String.format(BUSY_RESPONSE, busyWorkers).getBytes();
                log.info("handle -> {} workers active", busyWorkers);
            }

            httpExchange.getResponseHeaders().set("Content-Type", "application/json");
            httpExchange.sendResponseHeaders(200, response.length);
            try(OutputStream os = httpExchange.getResponseBody()) {
                os.write(response);
            }
        }
    }
}
