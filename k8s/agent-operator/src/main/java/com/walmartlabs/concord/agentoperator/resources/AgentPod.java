package com.walmartlabs.concord.agentoperator.resources;

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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.agentoperator.PodUtils;
import com.walmartlabs.concord.agentoperator.PodUtils.Output;
import com.walmartlabs.concord.agentoperator.crd.AgentPoolCRD;
import com.walmartlabs.concord.agentoperator.crd.AgentPoolConfiguration;
import com.walmartlabs.concord.agentoperator.scheduler.AgentPoolInstance;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

public final class AgentPod {

    public static final String TAGGED_FOR_REMOVAL_LABEL = "concordTaggedForRemoval";
    public static final String POOL_NAME_LABEL = "poolName";
    public static final String CONFIG_HASH_LABEL = "concordCfgHash";

    private static final String AGENT_CONTAINER_NAME = "agent";
    private static final String[] MAINTENANCE_MODE_CMD = {"curl", "-X", "POST", "http://127.0.0.1:8010/maintenance-mode"};

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static List<Pod> list(KubernetesClient client, String resourceName) {
        return client.pods()
                .withLabel(POOL_NAME_LABEL, resourceName)
                .list()
                .getItems();
    }

    public static void create(KubernetesClient client,
                              AgentPoolInstance poolInstance,
                              String podName,
                              String configMapName,
                              String hash) throws IOException {

        AgentPoolConfiguration spec = poolInstance.getResource().getSpec();

        String podYaml = objectMapper.writeValueAsString(spec.getPod())
                .replaceAll("%%podName%%", podName)
                .replaceAll("%%app%%", AgentPoolCRD.SERVICE_FULL_NAME)
                .replaceAll("%%" + POOL_NAME_LABEL + "%%", poolInstance.getName())
                .replaceAll("%%configMapName%%", configMapName)
                .replaceAll("%%" + CONFIG_HASH_LABEL + "%%", hash);

        client.pods().load(new ByteArrayInputStream(podYaml.getBytes())).create();
    }

    public static List<Pod> listPods(KubernetesClient client, String resourceName) {
        return client.pods()
                .withLabel(POOL_NAME_LABEL, resourceName)
                .list()
                .getItems();
    }

    public static MaintenanceMode enableMaintenanceMode(KubernetesClient client, String podName) throws IOException {
        Output out = PodUtils.exec(client, podName, AGENT_CONTAINER_NAME, MAINTENANCE_MODE_CMD);
        try {
            return objectMapper.readValue(out.getStdout(), MaintenanceMode.class);
        } catch (IOException e) {
            throw new IOException("Error while enabling the maintenance mode on " + podName + ": " + out.getStderr(), e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MaintenanceMode {

        private final boolean maintenanceMode;
        private final long workersAlive;

        public MaintenanceMode(@JsonProperty("maintenanceMode") boolean maintenanceMode,
                               @JsonProperty("workersAlive") long workersAlive) {

            this.maintenanceMode = maintenanceMode;
            this.workersAlive = workersAlive;
        }

        public boolean isMaintenanceMode() {
            return maintenanceMode;
        }

        public long getWorkersAlive() {
            return workersAlive;
        }

        @Override
        public String toString() {
            return "MaintenanceMode{" +
                    "maintenanceMode=" + maintenanceMode +
                    ", workersAlive=" + workersAlive +
                    '}';
        }
    }

    private AgentPod() {
    }
}
