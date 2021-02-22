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
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class GitClientTest5 {

    private GitClient client;

    @Before
    public void init() {
        client = new GitClient(GitClientConfiguration.builder()
                .sshTimeout(Duration.ofMinutes(10))
                .sshTimeoutRetryCount(1)
                .httpLowSpeedLimit(1)
                .httpLowSpeedTime(Duration.ofMinutes(10))
                .build());
    }

    @Test
    public void testFetch() throws Exception {
        Path repo = GitUtils.createBareRepository(resourceToPath("/master"));
        RevCommit commit0 = GitUtils.addContent(repo, resourceToPath("/test5/0_concord.yml"));
        RevCommit commit1 = GitUtils.addContent(repo, resourceToPath("/test5/1_concord.yml"));
        RevCommit commit2 = GitUtils.addContent(repo, resourceToPath("/test5/2_concord.yml"));
        List<RevCommit> commits = Arrays.asList(commit0, commit1, commit2);

        // fetch by commit + branch with clean repo
        for (int i = 0; i < 3; i++) {
            String commitId = commits.get(i).name();
            try (TemporaryPath repoPath = IOUtils.tempDir("git-client-test")) {
                String result = fetch(repo.toString(), "master", commitId, null, repoPath.path());
                assertContent(repoPath, i + "_concord.yml", i + "-concord-content");
                assertEquals(commitId, result);
            }
        }

        // fetch by commit  with clean repo
        for (int i = 0; i < 3; i++) {
            String commitId = commits.get(i).name();
            try (TemporaryPath repoPath = IOUtils.tempDir("git-client-test")) {
                String result = fetch(repo.toString(), null, commitId, null, repoPath.path());
                assertContent(repoPath, i + "_concord.yml", i + "-concord-content");
                assertEquals(commitId, result);
            }
        }
    }

    private String fetch(String repoUri, String branch, String commitId, Secret secret, Path dest) {
        return client.fetch(FetchRequest.builder()
                .url(repoUri)
                .version(FetchRequest.Version.commitWithBranch(commitId, branch))
                .secret(secret)
                .destination(dest)
                .shallow(true)
                .build()).head();
    }

    private static void assertContent(TemporaryPath repoPath, String path, String expectedContent) throws IOException {
        assertEquals(expectedContent, new String(Files.readAllBytes(repoPath.path().resolve(path))).trim());
    }

    private static Path resourceToPath(String resource) throws Exception {
        return Paths.get(GitClientTest5.class.getResource(resource).toURI());
    }
}
