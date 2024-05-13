package com.walmartlabs.concord.agentoperator.scheduler;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.agentoperator.crd.AgentPoolConfiguration;
import com.walmartlabs.concord.agentoperator.processqueue.ProcessQueueClient;
import com.walmartlabs.concord.agentoperator.resources.AgentPod;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.util.function.Function;

public class AutoScalerFactory {

    private final Function<String, Integer> podCounter;
    private final ProcessQueueClient processQueueClient;

    public AutoScalerFactory(Scheduler.Configuration cfg, KubernetesClient k8sClient) {
        this.podCounter = n -> AgentPod.list(k8sClient, n).size();
        this.processQueueClient = new ProcessQueueClient(cfg.getConcordBaseUrl(), cfg.getConcordApiToken());
    }

    public AutoScaler create(AgentPoolInstance poolInstance) {
        AgentPoolConfiguration cfg = poolInstance.getResource().getSpec();
        if (LinearAutoScaler.NAME.equals(cfg.getAutoScaleStrategy())) {
            return new LinearAutoScaler(processQueueClient, podCounter);
        }

        return new DefaultAutoScaler(processQueueClient, podCounter);
    }
}
