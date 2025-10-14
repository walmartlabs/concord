package com.walmartlabs.concord.repository;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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

import com.walmartlabs.concord.common.secret.BinaryDataSecret;
import com.walmartlabs.concord.sdk.Secret;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GitUriTest {

    private static final GitClientConfiguration cfg = GitClientConfiguration.builder()
            .oauthToken("mock-token")
            .build();
    private static final GitClient client = new GitClient(cfg);
    private static Secret secret = new BinaryDataSecret(new byte[0]);

    @Test
    void testSsh() {
        var sshWithUser = client.updateUrl("git@gitserver.local:my-org/my-repo.git", null);
        assertEquals("git@gitserver.local:my-org/my-repo.git", sshWithUser);

        var sshNoUser = client.updateUrl("gitserver.local:my-org/my-repo.git", null);
        assertEquals("gitserver.local:my-org/my-repo.git",
                sshNoUser);
    }

    @Test
    void testHttps() {
        var httpsDefault = client.updateUrl("https://gitserver.local/my-org/my-repo.git", null);
        assertEquals("https://mock-token@gitserver.local/my-org/my-repo.git", httpsDefault);
    }

    @Test
    void testHttpWithSecret() {
        var httpsSecret = client.updateUrl("https://gitserver.local/my-org/my-repo.git", secret);
        assertEquals("https://gitserver.local/my-org/my-repo.git", httpsSecret);
    }

    @Test
    void testUnrestrictedHost() {
        var anonAuth = client.updateUrl("https://elsewhere.local/my-org/my-repo.git", null);
        // backwards-compat, auth added
        assertEquals("https://mock-token@elsewhere.local/my-org/my-repo.git", anonAuth);

        var url2 = client.updateUrl("https://gitserver.local/my-org/my-repo.git", null);
        // auth added
        assertEquals("https://mock-token@gitserver.local/my-org/my-repo.git", url2);

    }

    @Test
    void testGitHostRestriction() {
        var restrictedClient = new GitClient(GitClientConfiguration.builder()
                .from(cfg)
                .addAuthorizedGitHosts("gitserver.local")
                .build());

        var anonAuth = restrictedClient.updateUrl("https://elsewhere.local/my-org/my-repo.git", null);
        // unchanged
        assertEquals("https://elsewhere.local/my-org/my-repo.git", anonAuth);

        var url2 = restrictedClient.updateUrl("https://gitserver.local/my-org/my-repo.git", null);
        // auth added
        assertEquals("https://mock-token@gitserver.local/my-org/my-repo.git", url2);
    }

}
