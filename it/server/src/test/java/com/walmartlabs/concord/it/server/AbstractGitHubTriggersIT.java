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

import com.google.common.collect.ImmutableMap;
import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.client.*;
import com.walmartlabs.concord.client.ProcessEntry.StatusEnum;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.it.common.GitHubUtils;
import com.walmartlabs.concord.it.common.GitUtils;
import com.walmartlabs.concord.it.common.ServerClient;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.RefSpec;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class AbstractGitHubTriggersIT extends AbstractServerIT {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.US);

    protected static String toRepoName(Path p) {
        return p.getParent() .getFileName()+ "/" + p.getFileName();
    }

    protected Path initRepo(String resource) throws Exception {
        Path src = Paths.get(AbstractGitHubTriggersIT.class.getResource(resource).toURI());
        return GitUtils.createBareRepository(src);
    }

    protected Path initProjectAndRepo(String orgName, String projectName, String repoName, String repoBranch, Path bareRepo) throws Exception {
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());

        RepositoryEntry repo = new RepositoryEntry()
                .setBranch(repoBranch)
                .setUrl(bareRepo.toAbsolutePath().toString());

        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setRawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE)
                .setRepositories(ImmutableMap.of(repoName, repo)));

        return bareRepo;
    }

    protected void createNewBranch(Path bareRepo, String branch, String resource) throws Exception {
        Path dir = IOUtils.createTempDir("git");

        Git git = Git.cloneRepository()
                .setDirectory(dir.toFile())
                .setURI(bareRepo.toAbsolutePath().toString())
                .call();

        git.checkout()
                .setCreateBranch(true)
                .setName(branch)
                .call();

        Path src = Paths.get(AbstractGitHubTriggersIT.class.getResource(resource).toURI());
        IOUtils.copy(src, dir, StandardCopyOption.REPLACE_EXISTING);

        git.add()
                .addFilepattern(".")
                .call();

        git.commit()
                .setMessage("adding files from " + resource)
                .call();

        git.push()
                .setRefSpecs(new RefSpec(branch + ":" + branch))
                .call();
    }

    protected void updateConcordYml(Path bareRepo, Map<String, String> values) throws Exception {
        Path dir = IOUtils.createTempDir("git");

        Git git = Git.cloneRepository()
                .setDirectory(dir.toFile())
                .setURI(bareRepo.toAbsolutePath().toString())
                .call();

        Path concordYml = dir.resolve("concord.yml");
        String s = new String(Files.readAllBytes(concordYml));
        for (Map.Entry<String, String> e : values.entrySet()) {
            s = s.replaceAll(e.getKey(), e.getValue());
        }
        Files.write(concordYml, s.getBytes(), StandardOpenOption.TRUNCATE_EXISTING);

        git.add()
                .addFilepattern(".")
                .call();

        git.commit()
                .setMessage("updating concord.yml")
                .call();

        git.push()
                .call();
    }

    protected void refreshRepo(String orgName, String projectName, String repoName) throws Exception {
        RepositoriesApi repoApi = new RepositoriesApi(getApiClient());
        repoApi.refreshRepository(orgName, projectName, repoName, true);
    }

    protected ProcessEntry waitForAProcess(String orgName, String projectName, String initiator, ProcessEntry after) throws Exception {
        ProcessV2Api processApi = new ProcessV2Api(getApiClient());
        while (true) {
            String afterCreatedAt = after != null ? after.getCreatedAt().format(DATE_TIME_FORMATTER) : null;
            List<ProcessEntry> l = processApi.list(null, orgName, null, projectName, null, null, afterCreatedAt, null, null, null, initiator, null, null, null, null);
            if (l.size() == 1 && isFinished(l.get(0).getStatus())) {
                return l.get(0);
            }

            Thread.sleep(1000);
        }
    }

    protected int waitForProcessesToFinish() throws Exception {
        ProcessV2Api processApi = new ProcessV2Api(getApiClient());
        while (true) {
            List<ProcessEntry> l = processApi.list(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

            boolean allDone = true;
            for (ProcessEntry e : l) {
                if (e.getStatus() == StatusEnum.NEW
                        || e.getStatus() == StatusEnum.PREPARING
                        || e.getStatus() == StatusEnum.ENQUEUED
                        || e.getStatus() == StatusEnum.STARTING
                        || e.getStatus() == StatusEnum.RUNNING) {

                    allDone = false;
                    break;
                }
            }

            if (allDone) {
                return l.size();
            }

            Thread.sleep(1000);
        }
    }

    protected void expectNoProcesses(String orgName) throws Exception {
        expectNoProceses(orgName, null, null);
    }

    protected void expectNoProceses(String orgName, String projectName, ProcessEntry after) throws Exception {
        ProcessV2Api processApi = new ProcessV2Api(getApiClient());
        String afterCreatedAt = after != null ? after.getCreatedAt().format(DATE_TIME_FORMATTER) : null;
        List<ProcessEntry> l = processApi.list(null, orgName, null, projectName, null, null, afterCreatedAt, null, null, null, null, null, null, null, null);
        assertEquals(0, l.size());
    }

    protected void sendEvent(String resource, String event, String... params) throws Exception {
        String payload = resourceToString("githubTests/events/direct_branch_push.json");
        if (params != null) {
            for (int i = 0; i < params.length; i += 2) {
                String k = params[i];
                String v = params[i + 1];
                payload = payload.replaceAll(k, v);
            }
        }

        ApiClient client = getApiClient();
        client.addDefaultHeader("X-Hub-Signature", "sha1=" + GitHubUtils.sign(payload));

        GitHubEventsApi eventsApi = new GitHubEventsApi(client);
        eventsApi.onEvent(payload, "abc", event);
    }

    protected void assertLog(ProcessEntry entry, String pattern) throws Exception {
        byte[] ab = getLog(entry.getLogFileName());
        ServerClient.assertLog(pattern, ab);
    }

    protected void waitForCompletion(ProcessEntry entry) throws Exception {
        ProcessApi processApi = new ProcessApi(getApiClient());
        ServerClient.waitForCompletion(processApi, entry.getInstanceId());
    }

    protected void deleteOrg(String orgName) throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.delete(orgName, "yes");
    }

    protected static String resourceToString(String resource) throws Exception {
        URL url = AbstractGitHubTriggersIT.class.getResource(resource);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = url.openStream()) {
            IOUtils.copy(in, out);
        }

        return new String(out.toByteArray());
    }

    protected boolean isFinished(StatusEnum status) {
        return status == StatusEnum.CANCELLED ||
                status == StatusEnum.FAILED ||
                status == StatusEnum.FINISHED ||
                status == StatusEnum.TIMED_OUT;
    }
}
