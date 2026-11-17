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

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeletePodChangeTest {

    @Test
    @SuppressWarnings("unchecked")
    void deletesTheNamedPod() {
        KubernetesClient client = mock(KubernetesClient.class);
        MixedOperation<Pod, PodList, PodResource> pods = mock(MixedOperation.class);
        PodResource podResource = mock(PodResource.class);
        when(client.pods()).thenReturn(pods);
        when(pods.withName("test-pool-00001")).thenReturn(podResource);

        new DeletePodChange("test-pool-00001").apply(client);

        verify(podResource).delete();
    }
}
