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

import com.walmartlabs.concord.agentoperator.resources.AgentPod;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class TryToDeletePodChange implements Change {

    private static final Logger log = LoggerFactory.getLogger(TryToDeletePodChange.class);

    private final String podName;

    public TryToDeletePodChange(String podName) {
        this.podName = podName;
    }

    @Override
    public void apply(KubernetesClient client) {
        Pod pod = client.pods().withName(podName).get();
        if (pod == null) {
            log.warn("apply ['{}'] -> pod doesn't exist, nothing to do", podName);
            return;
        }

        AgentPod.MaintenanceMode mmode = null;

        String phase = pod.getStatus().getPhase();
        if ("running".equalsIgnoreCase(phase)) {
            try {
                mmode = AgentPod.enableMaintenanceMode(client, podName);
                log.info("apply ['{}'] -> mmode enabled: {}", podName, mmode);
            } catch (IOException e) {
                log.warn("apply ['{}'] -> can't enable the maintenance mode, removing the pod immediately...", podName, e);
                // TODO retries?
            }
        }

        if (mmode == null || (mmode.isMaintenanceMode() && mmode.getWorkersAlive() <= 0)) {
            client.pods().withName(podName).delete();
            log.info("apply ['{}'] -> removed (former phase: {})", podName, phase);
        }
    }
}
