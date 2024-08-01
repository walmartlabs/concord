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
import com.walmartlabs.concord.agentoperator.agent.AgentClientFactory;
import com.walmartlabs.concord.agentoperator.HashUtils;
import com.walmartlabs.concord.agentoperator.resources.AgentConfigMap;
import com.walmartlabs.concord.agentoperator.resources.AgentPod;
import com.walmartlabs.concord.agentoperator.scheduler.AgentPoolInstance;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Planner {

    private static final Logger log = LoggerFactory.getLogger(Planner.class);

    private final KubernetesClient client;
    private final AgentClientFactory agentClientFactory;

    public Planner(KubernetesClient client, AgentClientFactory agentClientFactory) {
        this.client = client;
        this.agentClientFactory = agentClientFactory;
    }

    public List<Change> plan(AgentPoolInstance poolInstance) throws IOException {
        String resourceName = poolInstance.getName();

        List<Change> changes = new ArrayList<>();

        // process pods marked for removal first
        client.pods()
                .withLabel(AgentPod.TAGGED_FOR_REMOVAL_LABEL)
                .withLabel(AgentPod.POOL_NAME_LABEL, resourceName)
                .list()
                .getItems()
                .forEach(n -> changes.add(new TryToDeletePodChange(n.getMetadata().getName(), agentClientFactory.create(n))));

        List<Pod> pods = AgentPod.list(client, resourceName);
        int currentSize = pods.size();

        // hash of the Agent Pod configuration, will be used to determine which resources should be updated
        String newHash = HashUtils.hashAsHexString(poolInstance.getResource().getSpec().getPod());

        // calculate the configmap changes

        boolean recreateAllPods = false;

        String configMapName = configMapName(resourceName);
        ConfigMap m = AgentConfigMap.get(client, configMapName);

        int targetSize = poolInstance.getTargetSize();

        log.info("plan ['{}'] -> currentSize = {}, targetSize= {}, configMap = {}", resourceName, currentSize, targetSize, m != null);

        AgentPoolInstance.Status poolStatus = poolInstance.getStatus();
        if (poolStatus == AgentPoolInstance.Status.DELETED) {
            targetSize = 0;
        }

        /*
        Set the flag to recreate all pods to true, when there is a change to
        Config Map in the AgentPool definition, or when a new ConfigMap is added.

        Delete the Config Map if there are no pods present in the pool.
         */

        if (m == null) {
            if (targetSize > 0) {
                changes.add(new CreateConfigMapChange(poolInstance, configMapName));
                recreateAllPods = true;
            }
        } else {
            if (targetSize <= 0 && currentSize == 0) {
                changes.add(new DeleteConfigMapChange(configMapName));
            } else if (AgentConfigMap.hasChanges(client, poolInstance, m)) {
                changes.add(new DeleteConfigMapChange(configMapName));
                changes.add(new CreateConfigMapChange(poolInstance, configMapName));
                recreateAllPods = true;
            }
        }

        /*
        check all pods for cfg changes.

        Delete the pod only if there is a change in the Hash of Agent Pod,
        that is change to Pod definition in the AgentPool resource definition.

        Changes include change to the image, mem requirements, env variable changes,
        docker changes, or anything that requires the Agent Pod to be restarted.

        Do not delete and recreate all pods when there is a change to the
        agentpool sizes - min, max and current size.
         */

        for (Pod p : pods) {
            String currentHash = p.getMetadata().getLabels().get(AgentPod.CONFIG_HASH_LABEL);
            if (!newHash.equals(currentHash)) {
                changes.add(new TagForRemovalChange(p.getMetadata().getName(), agentClientFactory.create(p)));
            }
        }

        // recreate all pods if the configmap changed

        if (recreateAllPods) {
            pods.forEach(p -> changes.add(new TagForRemovalChange(p.getMetadata().getName(), agentClientFactory.create(p))));
        }

        // create or remove pods according to the configured pool size
        if (targetSize > currentSize) {
            Set<String> podNames = pods.stream().map(p -> p.getMetadata().getName()).collect(Collectors.toSet());
            for (int i = 0; i < targetSize - currentSize; i++) {
                String podName = generatePodName(resourceName, podNames);
                changes.add(new CreatePodChange(poolInstance, podName, configMapName(resourceName), newHash));
                podNames.add(podName);
            }
        }

        if (currentSize > targetSize) {
            int podsToDelete = currentSize - targetSize;
            for (Pod pod : pods) {
                if (pod.getMetadata().getLabels().containsKey(AgentPod.TAGGED_FOR_REMOVAL_LABEL)) {
                    continue;
                }

                String podName = pod.getMetadata().getName();
                AgentClient agentClient = agentClientFactory.create(pod);
                changes.add(new TagForRemovalChange(podName, agentClient));
                changes.add(new TryToDeletePodChange(podName, agentClient));

                podsToDelete--;
                if (podsToDelete == 0) {
                    break;
                }
            }
        }

        if (!changes.isEmpty()) {
            log.info("plan ['{}'] -> changes: {}", resourceName, changes);
        }

        return changes;
    }

    private static String generatePodName(String resourceName, Set<String> podNames) {
        for (int i = 0; i < podNames.size() + 1; i++) {
            String podName = podName(resourceName, i);

            if (!podNames.contains(podName)) {
                return podName;
            }
        }
        throw new RuntimeException("Can't generate pod name for '" + resourceName + "', current pods: " + podNames);
    }

    private static String configMapName(String resourceName) {
        return resourceName + "-cfg";
    }

    private static String podName(String resourceName, int i) {
        return String.format("%s-%05d", resourceName, i);
    }
}
