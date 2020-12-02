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
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

import static org.junit.Assert.assertEquals;

public class GitClientTest4 {

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
        Path tmpDir = IOUtils.createTempDir("test");

        File src = new File(GitClientTest4.class.getResource("/test4").toURI());
        IOUtils.copy(src.toPath(), tmpDir);

        // init repo
        Git repo = Git.init().setDirectory(tmpDir.toFile()).call();
        repo.add().addFilepattern(".").call();
        RevCommit commitId = repo.commit().setMessage("import").call();

        try (TemporaryPath repoPath = IOUtils.tempDir("git-client-test")) {
            // --- fetch master
            String actualCommitId = fetch(tmpDir.toUri().toString(), "master", null, null, repoPath.path());
            assertContent(repoPath, "concord.yml", "concord-init");
            assertEquals(commitId.name(), actualCommitId);

            // update file in repo
            Files.copy(tmpDir.resolve("new_concord.yml"), tmpDir.resolve("concord.yml"), StandardCopyOption.REPLACE_EXISTING);
            repo.add().addFilepattern(".").call();
            commitId = repo.commit().setMessage("update").call();

            // --- fetch master again
            actualCommitId = fetch(tmpDir.toUri().toString(), "master", null, null, repoPath.path());
            assertContent(repoPath, "concord.yml", "new-concord-content");
            assertEquals(commitId.name(), actualCommitId);
        }
    }

    private String fetch(String repoUri, String branch, String commitId, Secret secret, Path dest) {
        return client.fetch(FetchRequest.builder()
                .url(repoUri)
                .branchOrTag(branch)
                .commitId(commitId)
                .secret(secret)
                .destination(dest)
                .shallow(true)
                .build()).head();
    }

    private static void assertContent(TemporaryPath repoPath, String path, String expectedContent) throws IOException {
        assertEquals(expectedContent, new String(Files.readAllBytes(repoPath.path().resolve(path))).trim());
    }
}
