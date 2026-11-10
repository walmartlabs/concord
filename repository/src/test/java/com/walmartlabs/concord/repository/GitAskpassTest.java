package com.walmartlabs.concord.repository;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GitAskpassTest {

    @Test
    void testNoUrlCredentials() {
        assertNull(GitClient.credentialsFromUrl(null));
        assertNull(GitClient.credentialsFromUrl("https://gitserver.local/org/repo.git"));
        assertNull(GitClient.credentialsFromUrl("git@gitserver.local:org/repo.git"));
    }

    @Test
    void testUrlCredentials() {
        assertEquals(new GitClient.AskpassCredentials("my-token", "", "https://gitserver.local"),
                GitClient.credentialsFromUrl("https://my-token@gitserver.local/org/repo"));
        assertEquals(new GitClient.AskpassCredentials("us@er", "p'a:s$s", "https://gitserver.local:8443"),
                GitClient.credentialsFromUrl("https://us%40er:p'a%3As$s@gitserver.local:8443/org/repo.git"));
    }

    @Test
    void testUnrestricted() throws Exception {
        Path askpass = GitClient.createUnixStandardAskpass(new GitClient.AskpassCredentials("user", "p'ass", null));
        try {
            assertEquals("user", run(askpass, "Username for 'https://gitserver.local': "));
            assertEquals("p'ass", run(askpass, "Password for 'https://user@gitserver.local': "));
            assertEquals("user", run(askpass, "Username for 'https://elsewhere.local': "));
        } finally {
            Files.deleteIfExists(askpass);
        }
    }

    @Test
    void testRestrictedToOrigin() throws Exception {
        Path askpass = GitClient.createUnixStandardAskpass(
                GitClient.credentialsFromUrl("https://my-token@gitserver.local/org/repo"));
        try {
            // after a redirect git forgets the URL credentials and asks for both
            assertEquals("my-token", run(askpass, "Username for 'https://gitserver.local': "));
            assertEquals("", run(askpass, "Password for 'https://gitserver.local': "));
            // username known from the URL, only password requested
            assertEquals("", run(askpass, "Password for 'https://my-token@gitserver.local': "));

            // other origins get nothing
            assertEquals("", run(askpass, "Username for 'https://evil.local': "));
            assertEquals("", run(askpass, "Password for 'https://x@gitserver.local.evil': "));
            assertEquals("", run(askpass, "Username for 'http://gitserver.local': "));
            assertEquals("", run(askpass, "Username for 'https://gitserver.local:8443': "));
        } finally {
            Files.deleteIfExists(askpass);
        }
    }

    @Test
    void testRestrictedWithPortAndSpecialChars() throws Exception {
        Path askpass = GitClient.createUnixStandardAskpass(
                GitClient.credentialsFromUrl("https://us%40er:p'a%3As$s@gitserver.local:8443/org/repo.git"));
        try {
            assertEquals("us@er", run(askpass, "Username for 'https://gitserver.local:8443': "));
            assertEquals("p'a:s$s", run(askpass, "Password for 'https://gitserver.local:8443': "));
            assertEquals("p'a:s$s", run(askpass, "Password for 'https://us@er@gitserver.local:8443': "));
            assertEquals("", run(askpass, "Username for 'https://gitserver.local': "));
        } finally {
            Files.deleteIfExists(askpass);
        }
    }

    private static String run(Path askpass, String prompt) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(askpass.toAbsolutePath().toString(), prompt).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.waitFor());
        return out.endsWith("\n") ? out.substring(0, out.length() - 1) : out;
    }
}
