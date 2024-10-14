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

import io.fabric8.kubernetes.api.model.Pod;

import java.net.http.HttpClient;
import java.time.Duration;

import static java.net.http.HttpClient.Redirect.NEVER;

public class AgentClientFactory {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private final boolean useMaintenanceMode;
    private final HttpClient httpClient;

    public AgentClientFactory(boolean useMaintenanceMode) {
        this.useMaintenanceMode = useMaintenanceMode;
        if (useMaintenanceMode) {
            this.httpClient = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .followRedirects(NEVER)
                    .connectTimeout(CONNECT_TIMEOUT)
                    .build();
        } else {
            httpClient = null;
        }
    }

    public AgentClient create(Pod pod) {
        if (useMaintenanceMode && isRunning(pod) && hasIP(pod)) {
            return new DefaultAgentClient(httpClient, pod.getStatus().getPodIP());
        } else {
            return new NopAgentClient();
        }
    }

    private static boolean isRunning(Pod pod) {
        return pod.getStatus() != null && "Running".equals(pod.getStatus().getPhase());
    }

    private static boolean hasIP(Pod pod) {
        return pod.getStatus() != null && pod.getStatus().getPodIP() != null;
    }
}
