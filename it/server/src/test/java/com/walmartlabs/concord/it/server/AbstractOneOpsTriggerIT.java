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

import com.google.common.collect.ImmutableMap;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.*;
import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.client.*;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.it.common.GitUtils;
import com.walmartlabs.concord.it.common.ServerClient;

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
import static junit.framework.TestCase.assertNotNull;

public class AbstractOneOpsTriggerIT extends AbstractServerIT {

    protected void sendOneOpsEvent(String payloadPath) throws Exception {
        ApiClient apiClient = getApiClient();
        OkHttpClient httpClient = apiClient.getHttpClient();

        String payload = resourceToString(payloadPath);
        HttpUrl.Builder b = HttpUrl.parse(apiClient.getBasePath() + "/api/v1/events/oneops")
                .newBuilder();

        Request req = new Request.Builder()
                .url(b.build())
                .post(RequestBody.create(MediaType.parse("application/json"), payload))
                .header("Authorization", ServerClient.DEFAULT_API_KEY)
                .build();

        Response resp = httpClient.newCall(req).execute();
        if (!resp.isSuccessful()) {
            throw new RuntimeException("Request failed: " + resp);
        }
    }

    protected void assertProcessLog(ProcessEntry pir, String log) throws Exception {
        assertNotNull(pir);
        byte[] ab = getLog(pir.getLogFileName());
        assertLog(log, ab);
    }

    protected Map<ProcessEntry.StatusEnum, ProcessEntry> waitProcesses(
            String orgName, String projectName, ProcessEntry.StatusEnum first, ProcessEntry.StatusEnum... more) throws Exception {
        ProcessApi processApi = new ProcessApi(getApiClient());

        List<ProcessEntry> processes;
        while (true) {
            processes = processApi.list(orgName, projectName, null, null, null, null, null, null, null, null, null);
            if (processes.size() == 1 + (more != null ? more.length : 0)) {
                break;
            }
            Thread.sleep(1000);
        }

        Map<ProcessEntry.StatusEnum, ProcessEntry> ps = new HashMap<>();
        for (ProcessEntry p : processes) {
            ProcessEntry pir = waitForStatus(processApi, p.getInstanceId(), first, more);
            ProcessEntry pe = ps.put(pir.getStatus(), pir);
            if (pe != null) {
                throw new RuntimeException("already got process with '" + pe.getStatus() + "' status, id: " + pe.getInstanceId());
            }
        }
        return ps;
    }

    protected static String resourceToString(String resource) throws Exception {
        URL url = OneOpsTriggerITV2.class.getResource(resource);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = url.openStream()) {
            IOUtils.copy(in, out);
        }

        return new String(out.toByteArray());
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
                .setBranch("master")
                .setUrl(bareRepo.toAbsolutePath().toString());

        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setVisibility(ProjectEntry.VisibilityEnum.PUBLIC)
                .setRawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE)
                .setRepositories(ImmutableMap.of(repoName, repo)));

        return bareRepo;
    }

    protected List<TriggerEntry> waitForTriggers(String orgName, String projectName, String repoName, int expectedCount) throws Exception {
        TriggersApi triggerResource = new TriggersApi(getApiClient());
        while (true) {
            List<TriggerEntry> l = triggerResource.list(orgName, projectName, repoName);

            if (l != null && l.size() == expectedCount) {
                return l;
            }

            Thread.sleep(1000);
        }
    }
}
