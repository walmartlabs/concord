package com.walmartlabs.concord.agentoperator.agent;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class DefaultAgentClient implements AgentClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient client;
    private final String url;

    public DefaultAgentClient(HttpClient client, String podIp) {
        this.client = client;
        this.url = "http://%s:8010/maintenance-mode".formatted(podIp);
    }

    @Override
    public void enableMaintenanceMode() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(url))
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(REQUEST_TIMEOUT)
                .build();

        client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Override
    public boolean isNoWorkers() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(url))
                .GET()
                .timeout(REQUEST_TIMEOUT)
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        MaintenanceMode entity = objectMapper.readValue(response.body(), MaintenanceMode.class);
        return entity.maintenanceMode() && entity.workersAlive() == 0;
    }

    @Value.Immutable
    @JsonSerialize(as = ImmutableMaintenanceMode.class)
    @JsonDeserialize(as = ImmutableMaintenanceMode.class)
    public interface MaintenanceMode {

        boolean maintenanceMode();

        int workersAlive();
    }
}
