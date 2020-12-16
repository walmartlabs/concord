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

import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.client.*;
import com.walmartlabs.concord.common.IOUtils;
import org.eclipse.jgit.api.Git;
import org.junit.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.assertEquals;

public class ProcessCountIT extends AbstractServerIT {

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void test() throws Exception {
        Path tmpDir = createTempDir();

        File src = new File(TriggersRefreshIT.class.getResource("processCount").toURI());
        IOUtils.copy(src.toPath(), tmpDir);

        Git repo = Git.init().setDirectory(tmpDir.toFile()).call();
        repo.add().addFilepattern(".").call();
        repo.commit().setMessage("import").call();

        String gitUrl = tmpDir.toAbsolutePath().toString();

        // ---

        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        String projectName = "project_" + randomString();
        String repoName = "repo_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setRepositories(Collections.singletonMap(repoName, new RepositoryEntry()
                        .setUrl(gitUrl))));

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("repo", repoName);
        StartProcessResponse spr = start(input);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pe = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pe.getStatus());

        byte[] ab = getLog(pe.getLogFileName());
        assertLog(".*Hello!.*", ab);

        // ---

        ProcessV2Api processV2Api = new ProcessV2Api(getApiClient());
        List<ProcessEntry> l = processV2Api.list(null, orgName, null, projectName, null, repoName, null, null, null, null, null, null, null, null, null);
        assertEquals(1, l.size());
        assertEquals(pe.getInstanceId(), l.get(0).getInstanceId());

        // specifying an invalid repository name should return a 404 response
        try {
            processV2Api.list(null, orgName, null, projectName, null, repoName + randomString(), null, null, null, null, null, null, null, null, null);
        } catch (ApiException e) {
            assertEquals(404, e.getCode());
        }

        // ---

        int i = processV2Api.count(null, orgName, null, projectName, null, repoName, null, null, null, null, null, null);
        assertEquals(1, i);

        // specifying an invalid repository name should return a 404 response
        try {
            processV2Api.count(null, orgName, null, projectName, null, repoName + randomString(), null, null, null, null, null, null);
        } catch (ApiException e) {
            assertEquals(404, e.getCode());
        }
    }
}
