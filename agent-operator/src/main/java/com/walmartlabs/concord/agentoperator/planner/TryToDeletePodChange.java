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

import com.walmartlabs.concord.agentoperator.agent.AgentClient;
import com.walmartlabs.concord.agentoperator.PodLabels;
import com.walmartlabs.concord.agentoperator.resources.AgentPod;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class TryToDeletePodChange implements Change {

    private static final Logger log = LoggerFactory.getLogger(TryToDeletePodChange.class);

    private final String podName;
    private final AgentClient agentClient;

    public TryToDeletePodChange(String podName, AgentClient agentClient) {
        this.podName = podName;
        this.agentClient = agentClient;
    }

    /**
     * When the agent pod is being deleted, Kubernetes calls the prestop hook configured.
     * The prestop hook script configured for agent container enables maintenance mode on the agent,
     * and waits for the number of workers in use to go to 0, before the pod gets terminated.
     * <p>
     * Whenever the scheduler calls this `apply` method, the following conditions are checked, and
     * corresponding actions are executed.
     * <p>
     * - If the pod has a label `preStopHookTermination: true`, do nothing and exit, as the pod is being
     * terminated, waiting for the prestop hook script to complete (that is, last running process on the agent
     * container to complete).
     * <p>
     * - Otherwise if the pod is in `RUNNING` phase, call the kubernetes client `delete` method on the agent pod,
     * which will put the pod in `Terminating` state and start executing the prestop hook script on the
     * agent container. Add the label `preStopHookTermination: true` (this will be checked on
     * subsequent executions).
     *
     * @param client instance of Kubernetes client
     */
    @Override
    public void apply(KubernetesClient client) {
        Pod pod = client.pods().withName(podName).get();

        if (pod == null) {
            log.warn("apply ['{}'] -> pod doesn't exist, nothing to do", podName);
            return;
        }

        Map<String, String> labels = pod.getMetadata().getLabels();

        if ("true".equals(labels.getOrDefault(AgentPod.PRE_STOP_HOOK_TERMINATION_LABEL, "false"))) {
            log.warn("['{}'] -> has been marked for termination", podName);
            return;
        }

        try {
            if (agentClient.hasBusyWorkers()) {
                return;
            }
        } catch (Exception e) {
            log.error("Error while checking agent workers count for pod '{}'", podName, e);
            return;
        }

        // agent pod in maintenance mode and all workers done
        client.pods().withName(podName).delete();
        PodLabels.applyTag(client, podName, AgentPod.PRE_STOP_HOOK_TERMINATION_LABEL, "true");
        log.info("apply ['{}'] -> Marked for termination (former phase: {})", podName, pod.getStatus().getPhase());
    }

    @Override
    public String toString() {
        return "TryToDeletePodChange{" +
                "podName='" + podName + '\'' +
                '}';
    }
}
