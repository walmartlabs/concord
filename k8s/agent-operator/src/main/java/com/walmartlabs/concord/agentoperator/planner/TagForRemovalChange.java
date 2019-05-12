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
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class TagForRemovalChange implements Change {

    private static final Logger log = LoggerFactory.getLogger(TagForRemovalChange.class);

    private final String podName;

    public TagForRemovalChange(String podName) {
        this.podName = podName;
    }

    @Override
    public void apply(KubernetesClient client) {
        apply(client, podName);
    }

    public static void apply(KubernetesClient client, String podName) {
        Pod pod = client.pods().withName(podName).get();
        if (pod == null) {
            log.warn("apply ['{}'] -> pod doesn't exist, nothing to do", podName);
            return;
        }

        Map<String, String> labels = pod.getMetadata().getLabels();
        if (labels.containsKey(AgentPod.TAGGED_FOR_REMOVAL_LABEL)) {
            return;
        }

        try {
            labels.put(AgentPod.TAGGED_FOR_REMOVAL_LABEL, "true");
            client.pods().withName(podName).patch(pod);
            log.info("apply ['{}'] -> done", podName);
        } catch (KubernetesClientException e) {
            if (e.getCode() == 404) {
                log.warn("apply ['{}'] -> pod doesn't exist, nothing to do", podName);
            }
        }
    }
}
