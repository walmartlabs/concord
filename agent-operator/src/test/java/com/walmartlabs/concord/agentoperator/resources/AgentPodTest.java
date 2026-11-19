package com.walmartlabs.concord.agentoperator.resources;

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

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodListBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentPodTest {

    @Test
    @SuppressWarnings("unchecked")
    void listActiveSkipsTerminatedPods() {
        List<Pod> all = List.of(podInPhase("Running"), podInPhase("Failed"), podInPhase("Pending"), podInPhase("Succeeded"), podInPhase("Unknown"));

        KubernetesClient client = mock(KubernetesClient.class);
        MixedOperation<Pod, PodList, PodResource> pods = mock(MixedOperation.class);
        FilterWatchListDeletable<Pod, PodList, PodResource> listing = mock(FilterWatchListDeletable.class);
        when(listing.list()).thenReturn(new PodListBuilder().withItems(all).build());
        when(pods.withLabel(AgentPod.POOL_NAME_LABEL, "test-pool")).thenReturn(listing);
        when(client.pods()).thenReturn(pods);

        List<Pod> result = AgentPod.listActive(client, "test-pool");

        assertEquals(List.of("Running", "Pending", "Unknown"), result.stream().map(p -> p.getStatus().getPhase()).toList());
    }

    @Test
    void failedAndSucceededPodsAreTerminated() {
        assertTrue(AgentPod.isTerminated(podInPhase("Failed")));
        assertTrue(AgentPod.isTerminated(podInPhase("Succeeded")));
    }

    @Test
    void livePodsAreNotTerminated() {
        assertFalse(AgentPod.isTerminated(podInPhase("Pending")));
        assertFalse(AgentPod.isTerminated(podInPhase("Running")));
    }

    @Test
    void unknownPhaseIsNotTerminated() {
        assertFalse(AgentPod.isTerminated(podInPhase("Unknown")));
    }

    @Test
    void podWithoutStatusIsNotTerminated() {
        assertFalse(AgentPod.isTerminated(new PodBuilder().build()));
    }

    private static Pod podInPhase(String phase) {
        return new PodBuilder()
                .withNewStatus().withPhase(phase).endStatus()
                .build();
    }
}
