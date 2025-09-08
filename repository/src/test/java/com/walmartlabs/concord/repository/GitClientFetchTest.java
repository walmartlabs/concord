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
import com.walmartlabs.concord.repository.auth.HttpAuthProvider;
import com.walmartlabs.concord.sdk.Secret;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class GitClientFetchTest {

    private GitClient client;

    @Mock
    HttpAuthProvider authProvider;

    @BeforeEach
    public void init() {
        client = new GitClient(GitClientConfiguration.builder()
                .addAllAllowedSchemes(Arrays.asList("http", "https", "file"))
                .sshTimeout(Duration.ofMinutes(10))
                .sshTimeoutRetryCount(1)
                .httpLowSpeedLimit(1)
                .httpLowSpeedTime(Duration.ofMinutes(10))
                .build(), authProvider);
    }

    @Test
    public void testFetch1() throws Exception {
        Path tmpDir = IOUtils.createTempDir("test");

        IOUtils.copy(resourceToPath("/test4"), tmpDir);

        // init repo
        Git repo = Git.init().setInitialBranch("master").setDirectory(tmpDir.toFile()).call();
        repo.add().addFilepattern(".").call();
        RevCommit initialCommit = commit(repo, "import");

        try (TemporaryPath repoPath = IOUtils.tempDir("git-client-test")) {
            URI tmpDirURI = URI.create(repoPath.toString());
            // --- fetch master
            String actualCommitId = fetch(tmpDir, "master", null, null, repoPath.path());
            assertContent(repoPath, "concord.yml", "concord-init");
            assertEquals(initialCommit.name(), actualCommitId);

            // update file in repo
            Files.copy(tmpDir.resolve("new_concord.yml"), tmpDir.resolve("concord.yml"), StandardCopyOption.REPLACE_EXISTING);
            repo.add().addFilepattern(".").call();
            RevCommit commitAfterUpdate = commit(repo, "update");

            // --- fetch prev commit
            String prevCommit = fetch(tmpDir, "master", initialCommit.name(), null, repoPath.path());
            assertContent(repoPath, "concord.yml", "concord-init");
            assertEquals(initialCommit.name(), prevCommit);

            // --- fetch master again
            actualCommitId = fetch(tmpDir, "master", null, null, repoPath.path());
            assertContent(repoPath, "concord.yml", "new-concord-content");
            assertEquals(commitAfterUpdate.name(), actualCommitId);
        }
    }

    @Test
    public void testFetch2() throws Exception {
        Path repo = GitUtils.createBareRepository(resourceToPath("/master"));
        RevCommit commit0 = GitUtils.addContent(repo, resourceToPath("/test5/0_concord.yml"));
        RevCommit commit1 = GitUtils.addContent(repo, resourceToPath("/test5/1_concord.yml"));
        RevCommit commit2 = GitUtils.addContent(repo, resourceToPath("/test5/2_concord.yml"));
        List<RevCommit> commits = Arrays.asList(commit0, commit1, commit2);

        // fetch by commit + branch with clean repo
        for (int i = 0; i < 3; i++) {
            String commitId = commits.get(i).name();
            try (TemporaryPath repoPath = IOUtils.tempDir("git-client-test")) {
                String result = fetch(repo, "master", commitId, null, repoPath.path());
                assertContent(repoPath, i + "_concord.yml", i + "-concord-content");
                assertEquals(commitId, result);
            }
        }

        // fetch by commit with clean repo
        for (int i = 0; i < 3; i++) {
            String commitId = commits.get(i).name();
            try (TemporaryPath repoPath = IOUtils.tempDir("git-client-test")) {
                String result = fetch(repo, null, commitId, null, repoPath.path());
                assertContent(repoPath, i + "_concord.yml", i + "-concord-content");
                assertEquals(commitId, result);
            }
        }
    }

    @Test
    public void testFetch3() throws Exception {
        Path repo = GitUtils.createBareRepository(resourceToPath("/master"));
        GitUtils.createNewBranch(repo, "branch-1", resourceToPath("/branch-1"));
        GitUtils.createNewTag(repo, "tag-1", resourceToPath("/tag-1"));

        String commitId;
        String tagCommitId;

        try (TemporaryPath repoPath = IOUtils.tempDir("git-client-test")) {
            // fetch master
            fetch(repo, "master", null, null, repoPath.path());
            assertContent(repoPath, "master.txt", "master");

            // fetch branch
            commitId = fetch(repo, "branch-1", null, null, repoPath.path());
            assertContent(repoPath, "branch-1.txt", "branch-1");

            // fetch tag
            tagCommitId = fetch(repo, "tag-1", null, null, repoPath.path());
            assertContent(repoPath, "tag-1.txt", "tag-1");

            // fetch by commit
            fetch(repo, null, commitId, null, repoPath.path());
            assertContent(repoPath, "branch-1.txt", "branch-1");

            fetch(repo, null, tagCommitId, null, repoPath.path());
            assertContent(repoPath, "tag-1.txt", "tag-1");
        }

        // fetch by commit with clean repo
        try (TemporaryPath repoPath = IOUtils.tempDir("git-client-test")) {
            String result = fetch(repo, "branch-1", commitId, null, repoPath.path());
            assertContent(repoPath, "branch-1.txt", "branch-1");
            assertEquals(result, commitId);
        }

        // fetch by commit with clean repo
        try (TemporaryPath repoPath = IOUtils.tempDir("git-client-test")) {
            String result = fetch(repo, "tag-1", tagCommitId, null, repoPath.path());
            assertContent(repoPath, "tag-1.txt", "tag-1");
            assertEquals(result, tagCommitId);
        }

        // fetch by commit with clean repo and without branch -> should  fetch all repo and checkout commit-id
        try (TemporaryPath repoPath = IOUtils.tempDir("git-client-test")) {
            String result = fetch(repo, null, commitId, null, repoPath.path());
            assertContent(repoPath, "branch-1.txt", "branch-1");
            assertEquals(result, commitId);
        }

        // fetch same branch two times
        try (TemporaryPath repoPath = IOUtils.tempDir("git-client-test")) {
            // fetch branch
            fetch(repo, "branch-1", null, null, repoPath.path());
            assertContent(repoPath, "branch-1.txt", "branch-1");

            // fetch branch
            fetch(repo, "branch-1", null, null, repoPath.path());
            assertContent(repoPath, "branch-1.txt", "branch-1");
        }
    }

    private String fetch(Path path, String branch, String commitId, Secret secret, Path dest) {
        return client.fetch(FetchRequest.builder()
                .url("file://" + path)
                .version(FetchRequest.Version.commitWithBranch(commitId, branch))
                .secret(secret)
                .destination(dest)
                .shallow(true)
                .build()).head();
    }

    private static RevCommit commit(Git repo, String message) throws GitAPIException {
        return repo.commit()
                .setSign(false)
                .setMessage(message)
                .call();
    }

    private static void assertContent(TemporaryPath repoPath, String path, String expectedContent) throws IOException {
        assertEquals(expectedContent, new String(Files.readAllBytes(repoPath.path().resolve(path))).trim());
    }

    private static Path resourceToPath(String resource) throws Exception {
        return Paths.get(GitClientFetchTest.class.getResource(resource).toURI());
    }
}
