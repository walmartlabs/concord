package com.walmartlabs.concord.client;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import com.walmartlabs.concord.client2.ApiClient;
import com.walmartlabs.concord.runtime.v2.sdk.MapBackedVariables;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class RepositoryRefreshCommonTest {

    private static final UUID MOCK_REPO_ID_1 = UUID.randomUUID();
    private static final UUID MOCK_REPO_ID_2 = UUID.randomUUID();

    ArgumentCaptor<List<UUID>> listCaptor;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        listCaptor = ArgumentCaptor.forClass(List.class);
    }

    @Test
    void testPushMain() throws Exception {
        RepositoryRefreshTaskCommon common = getCommon();

        common.execute(params("main", false, true));

        verify(common, times(1))
                .refresh(listCaptor.capture());

        assertEquals(1, listCaptor.getValue().size());
        assertEquals(MOCK_REPO_ID_2, listCaptor.getValue().get(0));
    }

    @Test
    void testPushDev() throws Exception {
        RepositoryRefreshTaskCommon common = getCommon();

        common.execute(params("dev", false, true));

        verify(common, times(1))
                .refresh(listCaptor.capture());

        assertEquals(1, listCaptor.getValue().size());
        assertEquals(MOCK_REPO_ID_1, listCaptor.getValue().get(0));
    }

    @Test
    void testPushDevDisabled() throws Exception {
        RepositoryRefreshTaskCommon common = getCommon();

        common.execute(params("dev", false, false));

        verify(common, times(1))
                .refresh(listCaptor.capture());

        assertEquals(0, listCaptor.getValue().size());
    }

    @Test
    void testPushDeleteDev() throws Exception {
        RepositoryRefreshTaskCommon common = getCommon();

        common.execute(params("dev", true, true));

        verify(common, times(0))
                .refresh(listCaptor.capture());
    }

    private static RepositoryRefreshTaskCommon getCommon() {
        ApiClient apiClient = Mockito.mock(ApiClient.class);
        RepositoryRefreshTaskCommon common = spy(new RepositoryRefreshTaskCommon(apiClient));

        assertDoesNotThrow(() -> doNothing().when(common).refresh(any()));

        return common;
    }

    private static RepositoryRefreshTaskParams params(String branch, boolean deleted, boolean devEnabled) {
        Map<String, Object> rawParams = Map.of(
                "event", Map.of(
                        "type", "push",
                        "branch", branch,
                        "payload", Map.of(
                                "deleted", deleted
                        ),
                        "repositoryInfo", List.of(
                                Map.of(
                                        "repositoryId", MOCK_REPO_ID_1,
                                        "repository", "broken_repo",
                                        "projectId", UUID.randomUUID(),
                                        "branch", "dev",
                                        "enabled", devEnabled
                                ),
                                Map.of(
                                        "repositoryId", MOCK_REPO_ID_2,
                                        "repository", "main",
                                        "projectId", UUID.randomUUID(),
                                        "branch", "main",
                                        "enabled", true
                                )
                        )
                )
        );

        return new RepositoryRefreshTaskParams(new MapBackedVariables(rawParams));
    }
}
