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

import com.walmartlabs.concord.common.PathUtils;
import com.walmartlabs.concord.common.TemporaryPath;
import com.walmartlabs.concord.repository.auth.GitTokenProvider;
import com.walmartlabs.concord.sdk.Secret;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 OLD:          NEW:
 #1: 26 MB     #1: 14 MB
 #2: 26 MB     #2: 14 MB
 #3: 26 MB     #3: 15 MB
               #4: 40 MB
               #5: 40 MB
 #6: 36 MB     #6: 35 MB
 #7: 36 MB     #7: 35 MB
 #8: 36 MB     #8: 36 MB
 #9: 26 MB     #9: 14 MB
 #10: 26 MB    #10: 14 MB
 */

/**
 * Require internet connection
 */
@Disabled
@ExtendWith(MockitoExtension.class)
public class GitClientSpeedTest {

    private GitClient client;

    @Mock
    GitTokenProvider authProvider;

    @BeforeEach
    public void init() {
        client = new GitClient(GitClientConfiguration.builder()
                .sshTimeout(Duration.ofMinutes(10))
                .sshTimeoutRetryCount(1)
                .httpLowSpeedLimit(1)
                .httpLowSpeedTime(Duration.ofMinutes(10))
                .build(), authProvider);
    }

    @Test
    public void testFetch() throws Exception {
        String url = "https://github.com/walmartlabs/concord";
        try (TemporaryPath repoPath = PathUtils.tempDir("git-client-test")) {
            // fetch master
            fetch(url, "master", null, null, repoPath.path());
            assertContent(repoPath, "pom.xml", "<version>1.73.1-SNAPSHOT</version>");

            long size = FileUtils.sizeOfDirectory(repoPath.path().toFile());
            System.out.println("#1: " + FileUtils.byteCountToDisplaySize(size));

            // fetch branch
            fetch(url, "1.70.x", null, null, repoPath.path());
            assertContent(repoPath, "pom.xml", "<version>1.70.2-SNAPSHOT</version>");

            size = FileUtils.sizeOfDirectory(repoPath.path().toFile());
            System.out.println("#2: " + FileUtils.byteCountToDisplaySize(size));

            // fetch tag
            String tagCommitId = fetch(url, "1.73.0", null, null, repoPath.path());
            assertContent(repoPath, "pom.xml", "<version>1.73.0</version>");
            assertEquals("9c080a5e3a34dea8ae7c3b19b66a7c26e78a9c62", tagCommitId);

            size = FileUtils.sizeOfDirectory(repoPath.path().toFile());
            System.out.println("#3: " + FileUtils.byteCountToDisplaySize(size));

            // fetch by commit
            fetch(url, null, "071b966c36f30047aba6e94b49564a836c26bbd5", null, repoPath.path());
            assertContent(repoPath, "pom.xml", "<version>1.72.1-SNAPSHOT</version>");

            size = FileUtils.sizeOfDirectory(repoPath.path().toFile());
            System.out.println("#4: " + FileUtils.byteCountToDisplaySize(size));

            fetch(url, null, "910dbf2830f67b04ccbfd6aa0f2fad4aa5b54834", null, repoPath.path());
            assertContent(repoPath, "pom.xml", "<version>1.72.0</version>");

            size = FileUtils.sizeOfDirectory(repoPath.path().toFile());
            System.out.println("#5: " + FileUtils.byteCountToDisplaySize(size));
        }

        // fetch by commit with clean repo
        try (TemporaryPath repoPath = PathUtils.tempDir("git-client-test")) {
            String result = fetch(url, "1.63.x", "64feb9fe2d518e71a5497ba43132f4dafa1c471f", null, repoPath.path());
            assertContent(repoPath, "pom.xml", "<version>1.63.1</version>");
            assertEquals("64feb9fe2d518e71a5497ba43132f4dafa1c471f", result);

            long size = FileUtils.sizeOfDirectory(repoPath.path().toFile());
            System.out.println("#6: " + FileUtils.byteCountToDisplaySize(size));
        }

        // fetch by commit with clean repo
        try (TemporaryPath repoPath = PathUtils.tempDir("git-client-test")) {
            String result = fetch(url, "1.66.0", "56e248cf2e6fa10d058e41aac005b5dee70526e4", null, repoPath.path());
            assertContent(repoPath, "pom.xml", "<version>1.65.1-SNAPSHOT</version>");
            assertEquals("56e248cf2e6fa10d058e41aac005b5dee70526e4", result);

            long size = FileUtils.sizeOfDirectory(repoPath.path().toFile());
            System.out.println("#7: " + FileUtils.byteCountToDisplaySize(size));
        }

        // fetch by commit with clean repo and without branch -> should  fetch all repo and checkout commit-id
        try (TemporaryPath repoPath = PathUtils.tempDir("git-client-test")) {
            String result = fetch(url, null, "56e248cf2e6fa10d058e41aac005b5dee70526e4", null, repoPath.path());
            assertContent(repoPath, "pom.xml", "<version>1.65.1-SNAPSHOT</version>");
            assertEquals("56e248cf2e6fa10d058e41aac005b5dee70526e4", result);

            long size = FileUtils.sizeOfDirectory(repoPath.path().toFile());
            System.out.println("#8: " + FileUtils.byteCountToDisplaySize(size));
        }

        // fetch same branch two times
        try (TemporaryPath repoPath = PathUtils.tempDir("git-client-test")) {
            // fetch branch
            fetch(url, "1.70.x", null, null, repoPath.path());
            assertContent(repoPath, "pom.xml", "<version>1.70.2-SNAPSHOT</version>");

            long size = FileUtils.sizeOfDirectory(repoPath.path().toFile());
            System.out.println("#9: " + FileUtils.byteCountToDisplaySize(size));

            // fetch branch
            fetch(url, "1.70.x", null, null, repoPath.path());
            assertContent(repoPath, "pom.xml", "<version>1.70.2-SNAPSHOT</version>");

            size = FileUtils.sizeOfDirectory(repoPath.path().toFile());
            System.out.println("#10: " + FileUtils.byteCountToDisplaySize(size));
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
        String current = new String(Files.readAllBytes(repoPath.path().resolve(path)));
        assertTrue(current.contains(expectedContent));
    }
}
