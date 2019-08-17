package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.common.IOUtils.grep;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForStatus;

public class GeneralTriggerIT extends AbstractServerIT {

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testExclusive() throws Exception {
        Path tmpDir = createTempDir();

        File src = new File(TriggersRefreshIT.class.getResource("generalExclusiveTrigger").toURI());
        IOUtils.copy(src.toPath(), tmpDir);

        Git repo = Git.init().setDirectory(tmpDir.toFile()).call();
        repo.add().addFilepattern(".").call();
        repo.commit().setMessage("import").call();

        String gitUrl = tmpDir.toAbsolutePath().toString();

        // ---

        String orgName = "org_" + randomString();
        String projectName = "project_" + randomString();
        String repoName = "repo_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setVisibility(ProjectEntry.VisibilityEnum.PUBLIC)
                .setRepositories(Collections.singletonMap(repoName, new RepositoryEntry()
                        .setUrl(gitUrl))));

        // ---

        waitForTriggers(orgName, projectName, repoName, 1);

        // ---

        ExternalEventsApi eea = new ExternalEventsApi(getApiClient());
        Map<String, Object> eventParam = new HashMap<>();
        eventParam.put("key1", "value1");

        // first process
        eea.event("testTrigger", eventParam);

        // second process
        eea.event("testTrigger", eventParam);

        ProcessApi processApi = new ProcessApi(getApiClient());

        List<ProcessEntry> processes;
        while (true) {
            processes = processApi.list(orgName, projectName, null, null, null, null, null, null, null, null, null);

            if (processes.size() == 2) {
                break;
            }
            Thread.sleep(1000);
        }

        for (ProcessEntry p : processes) {
            ProcessEntry pir = waitForStatus(processApi, p.getInstanceId(), ProcessEntry.StatusEnum.FINISHED, ProcessEntry.StatusEnum.CANCELLED);
            byte[] ab = getLog(pir.getLogFileName());
            if (pir.getStatus() == ProcessEntry.StatusEnum.FINISHED) {
                assertLog(".*Hello from exclusive trigger.*", ab);
            } else {
                assertLog(".*Process\\(es\\) with exclusive group 'RED' is already in the queue. Current process has been cancelled.*", ab);
            }
        }

        // ---

        projectsApi.delete(orgName, projectName);
    }

    private List<TriggerEntry> waitForTriggers(String orgName, String projectName, String repoName, int expectedCount) throws Exception {
        TriggersApi triggerResource = new TriggersApi(getApiClient());
        while (true) {
            List<TriggerEntry> l = triggerResource.list(orgName, projectName, repoName);
            if (l != null && l.size() == expectedCount) {
                return l;
            }

            Thread.sleep(1000);
        }
    }

    private boolean hasLogEntry(ProcessEntry e, String pattern) throws Exception {
        byte[] ab = getLog(e.getLogFileName());
        List<String> l = grep(pattern, ab);
        return !l.isEmpty();
    }
}
