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

import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteConfigMapChange implements Change {

    private static final Logger log = LoggerFactory.getLogger(DeleteConfigMapChange.class);

    private final String configMapName;

    public DeleteConfigMapChange(String configMapName) {
        this.configMapName = configMapName;
    }

    @Override
    public void apply(KubernetesClient client) {
        if (client.configMaps().withName(configMapName).delete()) {
            // wait till it's actually removed
            while (client.configMaps().withName(configMapName).get() != null) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            log.info("apply -> removed a configmap {}", configMapName);
        }
    }

    @Override
    public String toString() {
        return "DeleteConfigMapChange{" +
                "configMapName='" + configMapName + '\'' +
                '}';
    }
}
