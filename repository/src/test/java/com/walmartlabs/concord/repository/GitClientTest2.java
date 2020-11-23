package com.walmartlabs.concord.repository;

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

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.common.TemporaryPath;
import com.walmartlabs.concord.sdk.Secret;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

import static org.junit.Assert.assertEquals;

public class GitClientTest2 {

    private static Path repo;
    private GitClient client;
    private GitClient2 client2;

    @BeforeClass
    public static void createRepo() throws Exception {
        repo = GitUtils.createBareRepository(resourceToPath("/master"));
        GitUtils.createNewBranch(repo, "branch-1", resourceToPath("/branch-1"));
        GitUtils.createNewTag(repo, "tag-1", resourceToPath("/tag-1"));
    }

    @Before
    public void init() {
        client = new GitClient(GitClientConfiguration.builder()
                .shallowClone(true)
                .sshTimeout(Duration.ofMinutes(10))
                .sshTimeoutRetryCount(1)
                .httpLowSpeedLimit(1)
                .httpLowSpeedTime(Duration.ofMinutes(10))
                .build());

        client2 = new GitClient2(GitClientConfiguration.builder()
                .shallowClone(true)
                .sshTimeout(Duration.ofMinutes(10))
                .sshTimeoutRetryCount(1)
                .httpLowSpeedLimit(1)
                .httpLowSpeedTime(Duration.ofMinutes(10))
                .build());
    }

    @Test
    public void testFetch() throws Exception {
        String commitId;
        String tagCommitId;

        try (TemporaryPath repoPath = IOUtils.tempDir("git-client-test")) {
            // fetch master
            fetch(repo.toString(), "master", null, null, repoPath.path());
            assertContent(repoPath, "master.txt", "master");

            // fetch branch
            commitId = fetch(repo.toString(), "branch-1", null, null, repoPath.path());
            assertContent(repoPath, "branch-1.txt", "branch-1");

            // fetch tag
            tagCommitId = fetch(repo.toString(), "tag-1", null, null, repoPath.path());
            assertContent(repoPath, "tag-1.txt", "tag-1");

            // fetch by commit
            fetch(repo.toString(), null, commitId, null, repoPath.path());
            assertContent(repoPath, "branch-1.txt", "branch-1");

            fetch(repo.toString(), null, tagCommitId, null, repoPath.path());
            assertContent(repoPath, "tag-1.txt", "tag-1");
        }

        // fetch by commit with clean repo
        try (TemporaryPath repoPath = IOUtils.tempDir("git-client-test")) {
            String result = fetch(repo.toString(), "branch-1", commitId, null, repoPath.path());
            assertContent(repoPath, "branch-1.txt", "branch-1");
            assertEquals(result, commitId);
        }

        // fetch by commit with clean repo
        try (TemporaryPath repoPath = IOUtils.tempDir("git-client-test")) {
            String result = fetch(repo.toString(), "tag-1", tagCommitId, null, repoPath.path());
            assertContent(repoPath, "tag-1.txt", "tag-1");
            assertEquals(result, tagCommitId);
        }

        // fetch by commit with clean repo and without branch -> should  fetch all repo and checkout commit-id
        try (TemporaryPath repoPath = IOUtils.tempDir("git-client-test")) {
            String result = fetch(repo.toString(), null, commitId, null, repoPath.path());
            assertContent(repoPath, "branch-1.txt", "branch-1");
            assertEquals(result, commitId);
        }

        // fetch same branch two times
        try (TemporaryPath repoPath = IOUtils.tempDir("git-client-test")) {
            // fetch branch
            fetch(repo.toString(), "branch-1", null, null, repoPath.path());
            assertContent(repoPath, "branch-1.txt", "branch-1");

            // fetch branch
            fetch(repo.toString(), "branch-1", null, null, repoPath.path());
            assertContent(repoPath, "branch-1.txt", "branch-1");
        }
    }

    private String fetch(String repoUri, String branch, String commitId, Secret secret, Path dest) {
//        return client.fetch(repoUri, branch, commitId, secret, dest);
        return client2.fetch(FetchRequest.builder()
                .url(repoUri)
                .branchOrTag(branch)
                .commitId(commitId)
                .secret(secret)
                .destination(dest)
                .shallow(true)
                .build()).head();
    }

    private static Path resourceToPath(String resource) throws Exception {
        return Paths.get(GitClientTest2.class.getResource(resource).toURI());
    }

    private static void assertContent(TemporaryPath repoPath, String path, String expectedContent) throws IOException {
        assertEquals(expectedContent, new String(Files.readAllBytes(repoPath.path().resolve(path))).trim());
    }
}
