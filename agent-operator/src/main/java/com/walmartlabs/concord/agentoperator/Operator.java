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
import com.walmartlabs.concord.agentoperator.crd.AgentPoolList;
import com.walmartlabs.concord.agentoperator.scheduler.AutoScalerFactory;
import com.walmartlabs.concord.agentoperator.scheduler.Scheduler;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.Executors;

import static com.walmartlabs.concord.agentoperator.scheduler.Event.Type.DELETED;
import static com.walmartlabs.concord.agentoperator.scheduler.Event.Type.MODIFIED;

public class Operator {

    private static final Logger log = LoggerFactory.getLogger(Operator.class);

    private static final long RESYNC_PERIOD = Duration.ofSeconds(10).toMillis();

    public static void main(String[] args) {
        var namespace = getEnv("WATCH_NAMESPACE", "default");
        var concordBaseUrl = getEnv("CONCORD_BASE_URL", "http://192.168.99.1:8001"); // use minikube/vbox host's default address
        var concordApiToken = getEnv("CONCORD_API_TOKEN", null);
        var useMaintenanceMode = Boolean.parseBoolean(getEnv("USE_AGENT_MAINTENANCE_MODE", "false"));

        var k8sClient = new DefaultKubernetesClient().inNamespace(namespace);
        var executor = Executors.newSingleThreadExecutor();

        var autoScalerFactory = new AutoScalerFactory(concordBaseUrl, concordApiToken, k8sClient);
        var scheduler = new Scheduler(autoScalerFactory, k8sClient, useMaintenanceMode);
        var handler = new ResourceEventHandler<AgentPool>() {
            @Override
            public void onAdd(AgentPool resource) {
                executor.submit(() -> scheduler.onEvent(MODIFIED, resource));
            }

            @Override
            public void onUpdate(AgentPool oldResource, AgentPool newResource) {
                if (oldResource == newResource) {
                    return;
                }
                executor.submit(() -> scheduler.onEvent(MODIFIED, newResource));
            }

            @Override
            public void onDelete(AgentPool resource, boolean deletedFinalStateUnknown) {
                executor.submit(() -> scheduler.onEvent(DELETED, resource));
            }
        };

        var informer = k8sClient.resources(AgentPool.class, AgentPoolList.class)
                .inAnyNamespace()
                .inform(handler, RESYNC_PERIOD);

        scheduler.start();

        try {
            informer.run();
        } catch (Exception e) {
            log.error("Error while watching for CRs (namespace={})", namespace, e);
            System.exit(2);
        }

        log.info("main -> and so my watch begins... (namespace={})", namespace);
    }

    private static String getEnv(String key, String defaultValue) {
        String s = System.getenv(key);
        if (s == null) {
            return defaultValue;
        }

        return s;
    }
}
