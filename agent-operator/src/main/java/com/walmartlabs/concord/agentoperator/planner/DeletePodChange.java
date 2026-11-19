package com.walmartlabs.concord.agentoperator.planner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeletePodChange implements Change {

    private static final Logger log = LoggerFactory.getLogger(DeletePodChange.class);

    private final String podName;

    public DeletePodChange(String podName) {
        this.podName = podName;
    }

    @Override
    public void apply(KubernetesClient client) {
        client.pods().withName(podName).delete();
        log.info("apply -> deleted a pod {}", podName);
    }

    @Override
    public String toString() {
        return "DeletePodChange{" +
                "podName='" + podName + '\'' +
                '}';
    }
}
