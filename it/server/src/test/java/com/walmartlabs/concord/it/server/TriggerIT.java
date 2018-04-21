package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.api.events.EventResource;
import com.walmartlabs.concord.server.api.org.OrganizationEntry;
import com.walmartlabs.concord.server.api.org.OrganizationResource;
import com.walmartlabs.concord.server.api.org.project.ProjectEntry;
import com.walmartlabs.concord.server.api.org.project.ProjectOperationResponse;
import com.walmartlabs.concord.server.api.org.project.ProjectResource;
import com.walmartlabs.concord.server.api.org.project.RepositoryEntry;
import com.walmartlabs.concord.server.api.org.trigger.TriggerEntry;
import com.walmartlabs.concord.server.api.org.trigger.TriggerResource;
import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessResource;
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

        OrganizationResource organizationResource = proxy(OrganizationResource.class);
        organizationResource.createOrUpdate(new OrganizationEntry(orgName));

        // ---

        String projectName = "project_" + randomString();
        String repoName = "repo_" + randomString();

        ProjectResource projectResource = proxy(ProjectResource.class);
        ProjectOperationResponse por = projectResource.createOrUpdate(orgName, new ProjectEntry(projectName,
                Collections.singletonMap(repoName, new RepositoryEntry(repoName, gitUrl))));

        // ---

        TriggerResource triggerResource = proxy(TriggerResource.class);
        while (true) {
            List<TriggerEntry> triggers = triggerResource.list(orgName, projectName, repoName);
            if (triggers != null && triggers.size() == 2) {
                break;
            }

            Thread.sleep(1000);
        }

        // ---

        EventResource eventResource = proxy(EventResource.class);
        eventResource.event("testTrigger", Collections.singletonMap("x", "abc"));

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);

        while (true) {
            List<ProcessEntry> l = processResource.list(por.getId(), null, null, 10);
            if (l != null && l.size() == 1) {
                break;
            }

            Thread.sleep(1000);
        }
    }
}
