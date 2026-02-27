package com.walmartlabs.concord.it.runtime.v2;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import ca.ibodrov.concord.testcontainers.ConcordProcess;
import ca.ibodrov.concord.testcontainers.Payload;
import ca.ibodrov.concord.testcontainers.junit5.ConcordRule;
import com.google.common.collect.ImmutableMap;
import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.it.common.GitUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.nio.file.Path;
import java.nio.file.Paths;

import static com.walmartlabs.concord.it.common.ITUtils.randomString;
import static org.junit.jupiter.api.Assertions.*;

public class RestartIT extends AbstractTest {

    @RegisterExtension
    public static final ConcordRule concord = ConcordConfiguration.configure();

    @Test
    public void testRestartWithDeletedRepo() throws Exception {
        // repo
        Path src = Paths.get(resource("restartWithDeletedRepo"));
        Path bareRepo = GitUtils.createBareRepository(src, concord.sharedContainerDir());

        // org + project + repository
        String orgName = "org_" + randomString();
        concord.organizations().create(orgName);

        String projectName = "project_" + randomString();
        String repoName = "repo_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(concord.apiClient());
        RepositoryEntry repo = new RepositoryEntry()
                .branch("master")
                .url(bareRepo.toAbsolutePath().toString());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE)
                .repositories(ImmutableMap.of(repoName, repo)));

        // ---
        ConcordProcess proc = concord.processes().start(new Payload()
                .org(orgName)
                .project(projectName)
                .parameter("repo", repoName));

        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);
        proc.assertLog(".*Hello from repo process!.*");

        // --
        RepositoriesApi repoApi = new RepositoriesApi(concord.apiClient());
        repoApi.deleteRepository(orgName, projectName, repoName);

        // restart should fail because the repository no longer exists
        ProcessApi processApi = new ProcessApi(concord.apiClient());
        processApi.restartProcess(proc.instanceId());

        expectStatus(proc, ProcessEntry.StatusEnum.FAILED);
        proc.assertLog(".*Repository not found.*");
    }
}
