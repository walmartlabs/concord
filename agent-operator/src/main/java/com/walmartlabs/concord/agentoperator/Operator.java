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


import com.walmartlabs.concord.agentoperator.agent.AgentClientFactory;
import com.walmartlabs.concord.agentoperator.crd.AgentPool;
import com.walmartlabs.concord.agentoperator.crd.AgentPoolList;
import com.walmartlabs.concord.agentoperator.scheduler.AutoScalerFactory;
import com.walmartlabs.concord.agentoperator.scheduler.Event;
import com.walmartlabs.concord.agentoperator.scheduler.Scheduler;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
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
        boolean useMaintenanceMode = Boolean.parseBoolean(getEnv("USE_AGENT_MAINTENANCE_MODE", "false"));

        // TODO use secrets for the token?
        Scheduler.Configuration cfg = new Scheduler.Configuration(baseUrl, apiToken);
        AutoScalerFactory autoScalerFactory = new AutoScalerFactory(cfg, client);
        AgentClientFactory agentClientFactory = new AgentClientFactory(useMaintenanceMode);
        Scheduler scheduler = new Scheduler(autoScalerFactory, client, agentClientFactory);
        scheduler.start();

        // TODO retries
        log.info("main -> my watch begins... (namespace={})", namespace);

        NonNamespaceOperation<AgentPool, AgentPoolList, Resource<AgentPool>> dummyClient = client.resources(AgentPool.class, AgentPoolList.class);
        dummyClient.watch(new Watcher<AgentPool>() {
            @Override
            public void eventReceived(Action action, AgentPool resource) {
                scheduler.onEvent(actionToEvent(action), resource);
            }

            @Override
            public void onClose(WatcherException we) {
                log.error("Watcher exception  {}", we.getMessage(), we);
            }
        });
    }

    private static Event.Type actionToEvent(Watcher.Action action) {
        switch (action) {
            case ADDED:
            case MODIFIED: {
                return Event.Type.MODIFIED;
            }
            case DELETED: {
                return Event.Type.DELETED;
            }
            default:
                throw new IllegalArgumentException("Unknown action type: " + action);
        }
    }

    private static String getEnv(String key, String defaultValue) {
        String s = System.getenv(key);
        if (s == null) {
            return defaultValue;
        }

        return s;
    }
}
