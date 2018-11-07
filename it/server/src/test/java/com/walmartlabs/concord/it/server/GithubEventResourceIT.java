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

import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.client.*;
import com.walmartlabs.concord.common.IOUtils;
import org.eclipse.jgit.api.Git;
import org.junit.Ignore;
import org.junit.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.common.IOUtils.grep;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GithubEventResourceIT extends AbstractServerIT {

    // form github.secret cfg
    private static final String GITHUB_WEBHOOK_SECRET = "12345";
    private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";

    @Test(timeout = 260000)
    public void pushUnknownRepo() throws Exception {
        String processTag = "tag_" + randomString();
        Path tmpDir = createTempDir();

        File src = new File(GithubEventResourceIT.class.getResource("githubEvent").toURI());
        IOUtils.copy(src.toPath(), tmpDir);
        updateTag(tmpDir.resolve("concord.yml"), processTag);

        Git repo = Git.init().setDirectory(tmpDir.toFile()).call();
        repo.add().addFilepattern(".").call();
        repo.commit().setMessage("import").call();

        String gitUrl = tmpDir.toAbsolutePath().toString();

        // ---

        String orgName = "Default";
        String projectName = "test_" + randomString();
        String repoName = "repo_" + randomString();
        String repoUrl = gitUrl;

        // ---

        UUID projectId = createProjectAndRepo(orgName, projectName, repoName, repoUrl);

        // ---

        String externalRepoName = "ext_" + randomString();
        githubEvent("githubEvent/push_unknown_repo.json", externalRepoName);

        List<ProcessEntry> processes = waitProcesses(projectId, processTag, 2);
        removeProcessWithLog(processes, ".*onOnlyUnknownRepo.*" + externalRepoName + ".*", 1);
        removeProcessWithLog(processes, ".*onAllRepo.*" + externalRepoName + ".*", 1);
        assertTrue(processes.toString(), processes.isEmpty());

        // ---

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.delete(orgName, projectName);
    }

    private void removeProcessWithLog(List<ProcessEntry> processes, String log, int expectedCount) throws Exception {
        Iterator<ProcessEntry> it = processes.iterator();
        while(it.hasNext()) {
            ProcessEntry psr = it.next();
            byte[] ab = getLog(psr.getLogFileName());
            int count = grep(log, ab).size();
            if (count == expectedCount) {
                it.remove();
            }
        }
    }

    private List<ProcessEntry> waitProcesses(UUID projectId, String processTag, int expectedCount) throws ApiException, InterruptedException {
        ProcessApi processApi = new ProcessApi(getApiClient());

        while (!Thread.currentThread().isInterrupted()) {
            List<ProcessEntry> process = processApi.list(projectId, null, null, Collections.singletonList(processTag), ProcessEntry.StatusEnum.FINISHED.getValue(), null, null, expectedCount + 1);
            if (process.size() == expectedCount) {
                return process;
            }

            Thread.sleep(1000);
        }

        throw new RuntimeException("oops");
    }

    private void updateTag(Path concord, String processTag) throws IOException {
        List<String> fileContent = Files.readAllLines(concord, StandardCharsets.UTF_8).stream()
            .map(l -> l.replaceAll(Pattern.quote("{{tag}}"), processTag))
            .collect(Collectors.toList());

        Files.write(concord, fileContent, StandardCharsets.UTF_8);
    }

    private void githubEvent(String eventFile, String repoName) throws Exception {
        String event = new String(Files.readAllBytes(Paths.get(GithubEventResourceIT.class.getResource(eventFile).toURI())));
        event = event.replace("org-repo", repoName);

        ApiClient client = getApiClient();
        client.addDefaultHeader("X-Hub-Signature", "sha1=" + sign(event));

        GitHubEventsApi gitHubEvents = new GitHubEventsApi(client);

        String result = gitHubEvents.onEvent(event, "push");
        assertEquals("ok", result);
    }

    private String sign(String payload) throws Exception {
        SecretKeySpec signingKey = new SecretKeySpec(GITHUB_WEBHOOK_SECRET.getBytes(), HMAC_SHA1_ALGORITHM);
        Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
        mac.init(signingKey);
        byte[] digest = mac.doFinal(payload.getBytes());
        return hex(digest);
    }

    private static String hex(byte[] str){
        return String.format("%040x", new BigInteger(1, str));
    }

    private UUID createProjectAndRepo(String orgName, String projectName, String repoName, String repoUrl) throws Exception {

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        ProjectOperationResponse cpr = projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setRepositories(Collections.singletonMap(repoName,
                        new RepositoryEntry()
                                .setName(repoName)
                                .setUrl(repoUrl))));
        assertTrue(cpr.isOk());

        // wait triggers created
        TriggersApi triggersApi = new TriggersApi(getApiClient());

        while (!Thread.currentThread().isInterrupted()) {
            List<TriggerEntry> triggers = triggersApi.list(orgName, projectName, repoName);
            if (!triggers.isEmpty()) {
                return null;
            }

            Thread.sleep(1000);
        }

        return cpr.getId();
    }
}
