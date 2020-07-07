package com.walmartlabs.concord.agentoperator;

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

import com.walmartlabs.concord.agentoperator.crd.AgentPool;
import com.walmartlabs.concord.agentoperator.crd.AgentPoolCRD;
import com.walmartlabs.concord.agentoperator.crd.AgentPoolList;
import com.walmartlabs.concord.agentoperator.crd.DoneableAgentPool;
import com.walmartlabs.concord.agentoperator.scheduler.Scheduler;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Operator {

    private static final Logger log = LoggerFactory.getLogger(Operator.class);

    public static void main(String[] args) {
        // TODO support overloading the CRD with an external file?

        String namespace = System.getenv("WATCH_NAMESPACE");
        if (namespace == null) {
            namespace = "default";
        }

        KubernetesClient client = new DefaultKubernetesClient() // NOSONAR
                .inNamespace(namespace);

        String baseUrl = getEnv("CONCORD_BASE_URL", "http://192.168.99.1:8001"); // use minikube/vbox host's default address
        String apiToken = getEnv("CONCORD_API_TOKEN", null);

        // TODO use secrets for the token?
        Scheduler.Configuration cfg = new Scheduler.Configuration(baseUrl, apiToken);
        Scheduler scheduler = new Scheduler(client, cfg);
        scheduler.start();

        // TODO retries
        log.info("main -> my watch begins... (namespace={})", namespace);
        client.customResources(AgentPoolCRD.SERVICE_DEFINITION, AgentPool.class, AgentPoolList.class, DoneableAgentPool.class)
                .watch(new ClusterWatcher(scheduler));
    }

    private static String getEnv(String key, String defaultValue) {
        String s = System.getenv(key);
        if (s == null) {
            return defaultValue;
        }

        return s;
    }
}
