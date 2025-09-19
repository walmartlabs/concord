package com.walmartlabs.concord.github.appinstallation;

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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URI;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.walmartlabs.concord.github.appinstallation.GitHubAppInstallation.extractOwnerAndRepo;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubAppInstallationTest {

    static Stream<URI> validPublicUris() {
        return Stream.of(
                "https://github.com/owner/repo.git", // typicalRepo
                "https://github.com/owner/repo",             // no trailing '.git', should still work
                "https://github.com/owner/repo/",            // ...same with trailing slash
                // with query params. not very typical, but does work in this pattern matching
                "https://github.com/owner/repo.git?hello=world",
                "https://github.com/owner/repo?hello=world",
                "https://github.com/owner/repo/?hello=world"
        ).map(URI::create);
    }

    static Stream<URI> invalidPublicUris() {
        return Stream.of(
                "https://github.com/owner", // no repo
                "https://github.com/owner/"         // no repo with slash
        ).map(URI::create);
    }

    static Stream<URI> validProxiedUris() {
        return Stream.of(
                "https://git.company.local/proxypath/owner/repo.git", // typicalRepo
                "https://git.company.local/proxypath/owner/repo",             // no trailing '.git', should still work
                "https://git.company.local/proxypath/owner/repo/",            // ...same with trailing slash
                // with query params. not very typical, but does work in this pattern matching
                "https://git.company.local/proxypath/owner/repo.git?hello=world",
                "https://git.company.local/proxypath/owner/repo?hello=world",
                "https://git.company.local/proxypath/owner/repo/?hello=world"
        ).map(URI::create);
    }

    static Stream<URI> invalidProxiedUris() {
        return Stream.of(
                "https://git.company.local/proxypath/owner", // no repo
                "https://git.company.local/proxypath/owner/"         // no repo with slash
        ).map(URI::create);
    }

    @ParameterizedTest
    @MethodSource("validPublicUris")
    void testValidPublicUris(URI repo) {
        var ownerAndRepo = assertDoesNotThrow(() -> runExtract("(?<baseUrl>github.com).*", repo));
        assertEquals("owner/repo", ownerAndRepo);
    }

    @ParameterizedTest
    @MethodSource("invalidPublicUris")
    void testInvalidPublicUris(URI repo) {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> runExtract("(?<baseUrl>github.com).*", repo));
        assertTrue(ex.getMessage().contains("Failed to parse owner and repository from path"));
    }

    @ParameterizedTest
    @MethodSource("validProxiedUris")
    void testValidProxiedUris(URI repo) {
        var ownerAndRepo = assertDoesNotThrow(() -> runExtract("(?<baseUrl>git.company.local/proxypath).*", repo));
        assertEquals("owner/repo", ownerAndRepo);
    }

    @ParameterizedTest
    @MethodSource("invalidProxiedUris")
    void testInvalidProxiedUris(URI repo) {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> runExtract("(?<baseUrl>git.company.local/proxypath).*", repo));
        assertTrue(ex.getMessage().contains("Failed to parse owner and repository from path"));
    }

    private static String runExtract(String pattern, URI repo) {
        var auth = getAuth(pattern);
        return extractOwnerAndRepo(auth, repo);
    }

    private static AppInstallationAuth getAuth(String urlPattern) {
        return AppInstallationAuth.builder()
                .urlPattern(Pattern.compile(urlPattern))
                .privateKey("/not/used")
                .clientId("1234")
                .apiUrl("https://api.github.com")
                .build();
    }
}
