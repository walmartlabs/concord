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

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public final class PodLabels {

    private static final Logger log = LoggerFactory.getLogger(PodLabels.class);

    public static void applyTag(KubernetesClient client, String podName, String tagName, String tagValue) {
        Pod pod = client.pods().withName(podName).get();
        if (pod == null) {
            log.warn("['{}']: apply tag ['{}': '{}'] -> pod doesn't exist, nothing to do", podName, tagName, tagValue);
            return;
        }

        Map<String, String> labels = pod.getMetadata().getLabels();
        if (labels.containsKey(tagName)) {
            return;
        }

        try {
            labels.put(tagName, tagValue);
            client.pods().withName(podName).patch(pod);
            log.info("['{}']: apply tag ['{}': '{}'] -> done", podName, tagName, tagValue);
        } catch (KubernetesClientException e) {
            if (e.getCode() == 404) {
                log.warn("['{}']: apply tag ['{}': '{}'] -> pod doesn't exist, nothing to do", podName, tagName, tagValue);
            } else {
                log.warn("['{}']: apply tag ['{}': '{}'] -> error", podName, tagName, tagValue, e);
            }
        }
    }

    private PodLabels() {
    }
}
