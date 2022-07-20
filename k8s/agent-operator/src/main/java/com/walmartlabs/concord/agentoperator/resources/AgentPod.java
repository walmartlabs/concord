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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.agentoperator.crd.AgentPool;
import com.walmartlabs.concord.agentoperator.crd.AgentPoolConfiguration;
import com.walmartlabs.concord.agentoperator.scheduler.AgentPoolInstance;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

public final class AgentPod {

    public static final String TAGGED_FOR_REMOVAL_LABEL = "concordTaggedForRemoval";
    public static final String PRE_STOP_HOOK_TERMINATION_LABEL = "preStopHookTermination";
    public static final String POOL_NAME_LABEL = "poolName";
    public static final String CONFIG_HASH_LABEL = "concordCfgHash";

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
                .replaceAll("%%app%%", AgentPool.SERVICE_FULL_NAME)
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

    private AgentPod() {
    }
}
