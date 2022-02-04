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

import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.client.*;
import com.walmartlabs.concord.common.IOUtils;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.walmartlabs.concord.common.IOUtils.grep;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class CronIT extends AbstractServerIT {

    // we need extra time for cron to fire up the processes
    @Test
    @Timeout(value = 4, unit = TimeUnit.MINUTES)
    public void testProfiles() throws Exception {
        Path tmpDir = createTempDir();

        File src = new File(TriggersRefreshIT.class.getResource("cronProfiles").toURI());
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
        if (orgApi.get(orgName) == null) {
            orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));
        }

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setVisibility(ProjectEntry.VisibilityEnum.PUBLIC)
                .setRepositories(Collections.singletonMap(repoName, new RepositoryEntry()
                        .setUrl(gitUrl)
                        .setBranch("master"))));

        // ---

        waitForTriggers(orgName, projectName, repoName, 2);

        // ---

        Set<String> expectedPatterns = new HashSet<>();
        expectedPatterns.add(".*Hello, AAA!.*");
        expectedPatterns.add(".*Hello, BBB!.*");

        while (true) {
            Thread.sleep(1000);

            List<ProcessEntry> aaaProcesses = listCronProcesses(orgName, projectName, repoName, "AAA");
            List<ProcessEntry> bbbProcesses = listCronProcesses(orgName, projectName, repoName, "BBB");

            if (aaaProcesses.isEmpty() || bbbProcesses.isEmpty()) {
                continue;
            }

            List<ProcessEntry> processes = Stream.concat(aaaProcesses.stream(), bbbProcesses.stream())
                    .collect(Collectors.toList());

            for (ProcessEntry e : processes) {
                assertNotEquals(ProcessEntry.StatusEnum.FAILED, e.getStatus());
            }

            Set<String> patterns = new HashSet<>(expectedPatterns);
            for (String p : patterns) {
                for (ProcessEntry e : processes) {
                    if (hasLogEntry(e, p)) {
                        expectedPatterns.remove(p);
                    }
                }
            }

            if (expectedPatterns.isEmpty()) {
                break;
            }
        }

        // --- clean up

        projectsApi.delete(orgName, projectName);
    }

    private List<ProcessEntry> listCronProcesses(String o, String p, String r, String tag) throws ApiException {
        ProcessV2Api processV2Api = new ProcessV2Api(getApiClient());

        return processV2Api.list(null, o, null, p, null, r, null, null,
                Collections.singletonList(tag), null, "cron", null, null, null, null);

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
