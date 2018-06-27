package com.walmartlabs.concord.it.server;

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


import com.walmartlabs.concord.client.*;
import com.walmartlabs.concord.common.IOUtils;
import org.eclipse.jgit.api.Git;
import org.junit.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public class TriggerIT extends AbstractServerIT {

    @Test(timeout = 60000)
    public void testInvalidConditionals() throws Exception {
        Path tmpDir = createTempDir();

        File src = new File(ProjectIT.class.getResource("invalidTriggers").toURI());
        IOUtils.copy(src.toPath(), tmpDir);

        Git repo = Git.init().setDirectory(tmpDir.toFile()).call();
        repo.add().addFilepattern(".").call();
        repo.commit().setMessage("import").call();

        String gitUrl = tmpDir.toAbsolutePath().toString();

        // ---

        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        // ---

        String projectName = "project_" + randomString();
        String repoName = "repo_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        ProjectOperationResponse por = projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setRepositories(Collections.singletonMap(repoName, new RepositoryEntry()
                        .setName(repoName)
                        .setUrl(gitUrl))));

        // ---

        TriggersApi triggerResource = new TriggersApi(getApiClient());
        while (true) {
            List<TriggerEntry> triggers = triggerResource.list(orgName, projectName, repoName);
            if (triggers != null && triggers.size() == 2) {
                break;
            }

            Thread.sleep(1000);
        }

        // ---

        ExternalEventsApi eventResource = new ExternalEventsApi(getApiClient());
        eventResource.event("testTrigger", Collections.singletonMap("x", "abc"));

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());

        while (true) {
            List<ProcessEntry> l = processApi.list(por.getId(), null, null, 10);
            if (l != null && l.size() == 1) {
                break;
            }

            Thread.sleep(1000);
        }
    }
}
