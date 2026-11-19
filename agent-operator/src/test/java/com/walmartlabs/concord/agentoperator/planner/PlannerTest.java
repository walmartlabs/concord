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


import com.walmartlabs.concord.agentoperator.HashUtils;
import com.walmartlabs.concord.agentoperator.agent.AgentClientFactory;
import com.walmartlabs.concord.agentoperator.crd.AgentPool;
import com.walmartlabs.concord.agentoperator.crd.AgentPoolConfiguration;
import com.walmartlabs.concord.agentoperator.resources.AgentPod;
import com.walmartlabs.concord.agentoperator.scheduler.AgentPoolInstance;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ConfigMapList;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodListBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlannerTest {

    private static final String POOL = "test-pool";
    private static final String CONFIG_MAP = POOL + "-cfg";

    @Test
    void healthyPoolHasNothingToDo() throws Exception {
        List<Change> changes = plan(2, pod("test-pool-00000", "Running"), pod("test-pool-00001", "Running"));

        assertEquals(List.of(), changes);
    }

    @Test
    void failedPodIsDeletedAndReplacedWithAFreshName() throws Exception {
        List<Change> changes = plan(2, pod("test-pool-00000", "Running"), pod("test-pool-00001", "Failed"));

        assertEquals(List.of("test-pool-00001"), podNames(changes, DeletePodChange.class));
        assertEquals(List.of("test-pool-00002"), podNames(changes, CreatePodChange.class));
    }

    @Test
    void succeededPodIsDeletedAndReplaced() throws Exception {
        List<Change> changes = plan(1, pod("test-pool-00000", "Succeeded"));

        assertEquals(List.of("test-pool-00000"), podNames(changes, DeletePodChange.class));
        assertEquals(List.of("test-pool-00001"), podNames(changes, CreatePodChange.class));
    }

    @Test
    void podWithUnknownPhaseIsLeftAlone() throws Exception {
        List<Change> changes = plan(1, pod("test-pool-00000", "Unknown"));

        assertEquals(List.of(), changes);
    }

    @Test
    void terminatedPodThatIsAlreadyBeingDeletedIsNotDeletedAgain() throws Exception {
        Pod terminating = new PodBuilder(pod("test-pool-00001", "Failed"))
                .editMetadata().withDeletionTimestamp("2026-11-05T12:00:00Z").endMetadata()
                .build();

        List<Change> changes = plan(2, pod("test-pool-00000", "Running"), terminating);

        assertEquals(List.of(), podNames(changes, DeletePodChange.class));
        assertEquals(List.of("test-pool-00002"), podNames(changes, CreatePodChange.class));
    }

    @Test
    void terminatedPodTaggedForRemovalIsDeletedWithoutAskingTheAgent() throws Exception {
        Pod tagged = new PodBuilder(pod("test-pool-00001", "Failed"))
                .editMetadata().addToLabels(AgentPod.TAGGED_FOR_REMOVAL_LABEL, "true").endMetadata()
                .build();

        List<Change> changes = plan(List.of(tagged), 1);

        assertEquals(List.of("test-pool-00001"), podNames(changes, DeletePodChange.class));
        assertEquals(List.of(), podNames(changes, TryToDeletePodChange.class));
        assertEquals(List.of("test-pool-00000"), podNames(changes, CreatePodChange.class));
    }

    private static List<Change> plan(int targetSize, Pod... pods) throws Exception {
        return plan(List.of(pods), targetSize);
    }

    private static List<Change> plan(List<Pod> pods, int targetSize) throws Exception {
        AgentPool pool = agentPool();
        AgentPoolInstance poolInstance = new AgentPoolInstance(POOL, pool, AgentPoolInstance.Status.ACTIVE, targetSize, 0, 0, 0);

        List<Pod> markedForRemoval = pods.stream()
                .filter(p -> p.getMetadata().getLabels().containsKey(AgentPod.TAGGED_FOR_REMOVAL_LABEL))
                .toList();

        return new Planner(clusterWith(markedForRemoval, pods), new AgentClientFactory(false)).plan(poolInstance);
    }

    @SuppressWarnings("unchecked")
    private static KubernetesClient clusterWith(List<Pod> markedForRemoval, List<Pod> allPods) {
        KubernetesClient client = mock(KubernetesClient.class);

        FilterWatchListDeletable<Pod, PodList, PodResource> allPodsListing = listing(allPods);
        FilterWatchListDeletable<Pod, PodList, PodResource> markedListing = listing(markedForRemoval);

        MixedOperation<Pod, PodList, PodResource> pods = mock(MixedOperation.class);
        when(client.pods()).thenReturn(pods);
        when(pods.withLabel(AgentPod.POOL_NAME_LABEL, POOL)).thenReturn(allPodsListing);

        FilterWatchListDeletable<Pod, PodList, PodResource> tagged = mock(FilterWatchListDeletable.class);
        when(pods.withLabel(AgentPod.TAGGED_FOR_REMOVAL_LABEL)).thenReturn(tagged);
        when(tagged.withLabel(AgentPod.POOL_NAME_LABEL, POOL)).thenReturn(markedListing);

        ConfigMap configMap = new ConfigMapBuilder().withNewMetadata().withName(CONFIG_MAP).endMetadata().withData(Map.of()).build();
        MixedOperation<ConfigMap, ConfigMapList, Resource<ConfigMap>> configMaps = mock(MixedOperation.class);
        Resource<ConfigMap> configMapResource = mock(Resource.class);
        when(client.configMaps()).thenReturn(configMaps);
        when(configMaps.withName(CONFIG_MAP)).thenReturn(configMapResource);
        when(configMaps.load(any(InputStream.class))).thenReturn(configMapResource);
        when(configMapResource.get()).thenReturn(configMap);
        when(configMapResource.item()).thenReturn(configMap);

        return client;
    }

    @SuppressWarnings("unchecked")
    private static FilterWatchListDeletable<Pod, PodList, PodResource> listing(List<Pod> pods) {
        FilterWatchListDeletable<Pod, PodList, PodResource> result = mock(FilterWatchListDeletable.class);
        when(result.list()).thenReturn(new PodListBuilder().withItems(pods).build());
        return result;
    }

    private static Pod pod(String name, String phase) {
        try {
            return new PodBuilder()
                    .withNewMetadata()
                    .withName(name)
                    .addToLabels(AgentPod.POOL_NAME_LABEL, POOL)
                    .addToLabels(AgentPod.CONFIG_HASH_LABEL, HashUtils.hashAsHexString(agentPool().getSpec().getPod()))
                    .endMetadata()
                    .withNewStatus().withPhase(phase).endStatus()
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static AgentPool agentPool() {
        AgentPool result = new AgentPool();
        result.setMetadata(new ObjectMeta());
        result.getMetadata().setNamespace("test-ns");
        result.getMetadata().setName(POOL);
        result.setSpec(new AgentPoolConfiguration());
        return result;
    }

    private static List<String> podNames(List<Change> changes, Class<? extends Change> type) {
        return changes.stream()
                .filter(type::isInstance)
                .map(Object::toString)
                .map(s -> s.replaceAll(".*podName='([^']+)'.*", "$1"))
                .toList();
    }
}
