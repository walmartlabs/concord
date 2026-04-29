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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.agentoperator.HashUtils;
import com.walmartlabs.concord.agentoperator.crd.AgentPoolConfiguration;
import com.walmartlabs.concord.agentoperator.scheduler.AgentPoolInstance;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public final class AgentConfigMap {

    private static final Logger log = LoggerFactory.getLogger(AgentConfigMap.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static ConfigMap get(KubernetesClient client, String configMapName) {
        try {
            return client.configMaps().withName(configMapName).get();
        } catch (KubernetesClientException e) {
            log.warn("get ['{}'] -> error while getting a configmap: {}", configMapName, e.getMessage());
            throw e;
        }
    }

    public static void create(KubernetesClient client, AgentPoolInstance poolInstance, String configMapName) throws IOException {
        try {
            ConfigMap m = prepare(client, poolInstance, configMapName);
            client.configMaps().resource(m).create();
        } catch (KubernetesClientException e) {
            log.warn("create ['{}', '{}'] -> error while creating a configmap: {}", poolInstance.getName(), configMapName, e.getMessage());
            throw e;
        }
    }

    public static void delete(KubernetesClient client, String configMapName) {
        try {
            client.configMaps().withName(configMapName).delete();

            // wait till it's actually removed
            while (client.configMaps().withName(configMapName).get() != null) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        } catch (KubernetesClientException e) {
            log.warn("delete ['{}'] -> error while deleting a configmap: {}", configMapName, e.getMessage());
            throw e;
        }
    }

    public static boolean hasChanges(KubernetesClient client, AgentPoolInstance poolInstance, ConfigMap a) throws IOException {
        ConfigMap b = prepare(client, poolInstance, a.getMetadata().getName());

        String hashA = HashUtils.hashAsHexString(a.getData());
        String hashB = HashUtils.hashAsHexString(b.getData());

        return !hashA.equals(hashB);
    }

    private static ConfigMap prepare(KubernetesClient client, AgentPoolInstance poolInstance, String configMapName) throws IOException {
        try {
            AgentPoolConfiguration spec = poolInstance.getResource().getSpec();

            String configMapYaml = objectMapper.writeValueAsString(spec.getConfigMap())
                    .replaceAll("%%configMapName%%", configMapName)
                    .replace("%%preStopHook%%", escape(Resources.get("/prestop-hook.sh")));

            return client.configMaps().load(new ByteArrayInputStream(configMapYaml.getBytes())).item();
        } catch (KubernetesClientException e) {
            log.warn("prepare ['{}', '{}'] -> error while preparing a configmap: {}", poolInstance.getName(), configMapName, e.getMessage());
            throw e;
        }
    }

    private static String escape(String str) throws JsonProcessingException {
        String result = objectMapper.writeValueAsString(str);
        return result.substring(1, result.length() - 1);
    }

    private AgentConfigMap() {
    }
}
