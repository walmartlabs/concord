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

import java.io.IOException;

public class Operator {

    private static final Logger log = LoggerFactory.getLogger(Operator.class);

    public static void main(String[] args) throws IOException {
        // TODO support overloading the CRD with an external file?

        String namespace = System.getenv("WATCH_NAMESPACE");
        if (namespace == null) {
            namespace = "default";
        }

        KubernetesClient client = new DefaultKubernetesClient()
                .inNamespace(namespace);

        // TODO use secrets for the token?
        Scheduler.Configuration cfg = new Scheduler.Configuration(System.getenv("CONCORD_BASE_URL"), System.getenv("CONCORD_API_TOKEN"));
        Scheduler scheduler = new Scheduler(client, cfg);
        scheduler.start();

        // TODO retries
        log.info("main -> my watch begins... (namespace={})", namespace);
        client.customResources(AgentPoolCRD.SERVICE_DEFINITION, AgentPool.class, AgentPoolList.class, DoneableAgentPool.class)
                .watch(new ClusterWatcher(scheduler));
    }
}
