package com.walmartlabs.concord.repository;

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

import com.walmartlabs.concord.common.PathUtils;
import com.walmartlabs.concord.common.TemporaryPath;
import com.walmartlabs.concord.common.secret.KeyPair;
import com.walmartlabs.concord.common.secret.UsernamePassword;
import com.walmartlabs.concord.sdk.Secret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled
public class GitClientRealTest {

    private static final String HTTPS_REPO_URL = System.getenv("HTTPS_REPO_URL");
    private static final String SSH_REPO_URL = System.getenv("SSH_REPO_URL");

    private static final String HTTPS_SUBMODULE_REPO_URL = System.getenv("HTTPS_SUBMODULE_REPO_URL");

    private static final Secret USERNAME_PASSWORD = new UsernamePassword(System.getenv("GIT_TEST_USER"), System.getenv("GIT_TEST_USER_PASSWD").toCharArray());
    private static final Secret KEYPAIR = createKeypair();

    private static Secret createKeypair() {
        try {
            return new KeyPair(
                    Files.readAllBytes(Paths.get(System.getenv("GIT_TEST_PUBLIC_KEY"))),
                    Files.readAllBytes(Paths.get(System.getenv("GIT_TEST_PRIVATE_KEY")))
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private GitClient client;

    @BeforeEach
    public void init() {
        client = new GitClient(GitClientConfiguration.builder()
                .oauthToken(System.getenv("GIT_TEST_OAUTH_TOKEN"))
                .sshTimeout(Duration.ofMinutes(10))
                .sshTimeoutRetryCount(1)
                .httpLowSpeedLimit(1)
                .httpLowSpeedTime(Duration.ofMinutes(10))
                .build());
    }

    @Test
    public void testFetchBranch() throws Exception {
        String branch = "master";

        assertFetchHttps(branch, null, "master");
        assertFetchSsh(branch, null, "master");
    }

    @Test
    public void testFetchTag() throws Exception {
        String tag = "test/tag-1";

        assertFetchHttps(tag, null, "tag-1");
        assertFetchSsh(tag, null, "tag-1");
    }

    @Test
    public void testFetchCommitId() throws Exception {
        String commitId = System.getenv("GIT_TEST_COMMIT_ID");

        assertFetchHttps(null, commitId, "commit-id");
        assertFetchSsh(null, commitId, "commit-id");
    }

    @Test
    public void testFetchWithSubmodules() throws Exception {
        String branch = "master";
        String commitId = null;

        // with default oauth token
        Secret secret = null;
        try (TemporaryPath repoPath = PathUtils.tempDir("git-client-test")) {
            fetch(HTTPS_SUBMODULE_REPO_URL, branch, commitId, secret, repoPath.path());

            assertEquals("master", new String(Files.readAllBytes(repoPath.path().resolve("test"))).trim());
            assertEquals("master", new String(Files.readAllBytes(repoPath.path().resolve("concord_poc").resolve("test"))).trim());
        }
    }

    private void assertFetchHttps(String branchOrTag, String commitId, String expected) throws IOException {
        // with default oauth token
        Secret secret = null;
        assertFetch(HTTPS_REPO_URL, branchOrTag, commitId, secret, expected);

        // with username/password
        assertFetch(HTTPS_REPO_URL, branchOrTag, commitId, USERNAME_PASSWORD, expected);
    }

    private void assertFetchSsh(String branchOrTag, String commitId, String expected) throws IOException {
        // with key pair
        assertFetch(SSH_REPO_URL, branchOrTag, commitId, KEYPAIR, expected);
    }

    private void assertFetch(String url, String branch, String commitId, Secret secret, String expectedContent) throws IOException {
        try (TemporaryPath repoPath = PathUtils.tempDir("git-client-test")) {
            fetch(url, branch, commitId, secret, repoPath.path());

            assertEquals(expectedContent, new String(Files.readAllBytes(repoPath.path().resolve("test"))).trim());
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
}
