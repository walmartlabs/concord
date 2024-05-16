package com.walmartlabs.concord.agentoperator.planner;

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

import com.walmartlabs.concord.agentoperator.resources.AgentConfigMap;
import com.walmartlabs.concord.agentoperator.scheduler.AgentPoolInstance;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class CreateConfigMapChange implements Change {

    private static final Logger log = LoggerFactory.getLogger(CreateConfigMapChange.class);

    private final AgentPoolInstance poolInstance;
    private final String configMapName;

    public CreateConfigMapChange(AgentPoolInstance poolInstance, String configMapName) {
        this.poolInstance = poolInstance;
        this.configMapName = configMapName;
    }

    @Override
    public void apply(KubernetesClient client) {
        try {
            AgentConfigMap.create(client, poolInstance, configMapName);
            log.info("apply -> created a configmap {}", configMapName);
        } catch (IOException e) {
            log.error("apply -> error while creating a configmap {}: {}", configMapName, e.getMessage());
        }
    }

    @Override
    public String toString() {
        return "CreateConfigMapChange{" +
                "configMapName='" + configMapName + '\'' +
                '}';
    }
}
