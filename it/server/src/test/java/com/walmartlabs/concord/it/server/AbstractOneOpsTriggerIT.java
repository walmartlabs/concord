package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.it.common.GitUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForStatus;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public abstract class AbstractOneOpsTriggerIT extends AbstractServerIT {

    protected void sendOneOpsEvent(String payloadPath) throws Exception {
        ExternalEventsApi api = new ExternalEventsApi(getApiClient());

        api.externalEvent("oneops", resourceToMap(payloadPath));
    }

    protected void assertProcessLog(ProcessEntry pir, String log) throws Exception {
        assertNotNull(pir);
        byte[] ab = getLog(pir.getInstanceId());
        assertLog(log, ab);
    }

    protected Map<ProcessEntry.StatusEnum, ProcessEntry> waitProcesses(
            String orgName, String projectName, ProcessEntry.StatusEnum first, ProcessEntry.StatusEnum... more) throws Exception {
        ProcessV2Api processApi = new ProcessV2Api(getApiClient());

        List<ProcessEntry> processes;
        while (true) {
            processes = processApi.listProcesses(ProcessListFilter.builder()
                            .orgName(orgName)
                            .projectName(projectName)
                    .build());
            if (processes.size() == 1 + (more != null ? more.length : 0)) {
                break;
            }
            Thread.sleep(1000);
        }

        Map<ProcessEntry.StatusEnum, ProcessEntry> ps = new HashMap<>();
        for (ProcessEntry p : processes) {
            ProcessEntry pir = waitForStatus(getApiClient(), p.getInstanceId(), first, more);
            ProcessEntry pe = ps.put(pir.getStatus(), pir);
            if (pe != null) {
                throw new RuntimeException("already got process with '" + pe.getStatus() + "' status, id: " + pe.getInstanceId());
            }
        }
        return ps;
    }

    protected static String resourceToString(String resource) throws Exception {
        URL url = AbstractOneOpsTriggerIT.class.getResource(resource);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = url.openStream()) {
            IOUtils.copy(in, out);
        }

        return new String(out.toByteArray());
    }

    @SuppressWarnings("unchecked")
    protected static Map<String, Object> resourceToMap(String resource) throws Exception {
        URL url = AbstractOneOpsTriggerIT.class.getResource(resource);
        try (InputStream in = url.openStream()) {
            return new ObjectMapper().readValue(in, Map.class);
        }
    }

    protected void refreshRepo(String orgName, String projectName, String repoName) throws Exception {
        RepositoriesApi repoApi = new RepositoriesApi(getApiClient());
        repoApi.refreshRepository(orgName, projectName, repoName, true);
    }

    protected Path initRepo(String resource) throws Exception {
        Path src = Paths.get(AbstractGitHubTriggersIT.class.getResource(resource).toURI());
        return GitUtils.createBareRepository(src);
    }

    protected Path initProjectAndRepo(String orgName, String projectName, String repoName, Path bareRepo) throws Exception {
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());

        RepositoryEntry repo = new RepositoryEntry()
                .branch("master")
                .url("file://" + bareRepo.toAbsolutePath().toString());

        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .visibility(ProjectEntry.VisibilityEnum.PUBLIC)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE)
                .repositories(ImmutableMap.of(repoName, repo)));

        return bareRepo;
    }

    protected List<TriggerEntry> waitForTriggers(String orgName, String projectName, String repoName, int expectedCount) throws Exception {
        TriggersApi triggerResource = new TriggersApi(getApiClient());
        while (true) {
            List<TriggerEntry> l = triggerResource.listTriggers(orgName, projectName, repoName);

            if (l != null && l.size() == expectedCount) {
                return l;
            }

            Thread.sleep(1000);
        }
    }
}
