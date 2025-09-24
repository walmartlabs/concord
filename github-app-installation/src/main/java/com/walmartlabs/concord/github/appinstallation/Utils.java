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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.common.secret.BinaryDataSecret;
import com.walmartlabs.concord.github.appinstallation.exception.GitHubAppException;
import com.walmartlabs.concord.github.appinstallation.exception.RepoExtractionException;
import com.walmartlabs.concord.sdk.Secret;

import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

public class Utils {

    /**
     * Validates given secret is usable enough to attempt a remote lookup. Not
     * guaranteed to actually work, just a sanity check useful to avoid attempting
     * API calls with something that will definitely not work.
     */
    static boolean validateSecret(Secret secret, ObjectMapper mapper) {
        // secret must be one of:
        // * JSON-formatted GitHub app installation details: clientId, privateKey, urlPattern
        // * single-line, plaintext access token

        if (secret == null) {
            return false;
        }

        if (!(secret instanceof BinaryDataSecret bds)) {
            // this class is not the place for handling key pairs or username/password
            return false;
        }

        var base = parseRawAppInstallation(bds, mapper);
        if (base == null) {
            // It's not JSON, may be an oauth token
            return isPrintableAscii(bds.getData());
        } else if (base.isEmpty()) {
            // Doesn't match something we can parse
            return false;
        }

        // App installation config format is either valid or not
        return parseAppInstallation(bds, mapper).isPresent();
    }

    private static boolean isPrintableAscii(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return false;
        }

        for (byte b : bytes) {
            // Cast byte to int to avoid issues with negative byte values
            int asciiValue = b & 0xFF; // Use bitwise AND to get unsigned value

            if (asciiValue < 32 || asciiValue > 126) {
                return false;
            }
        }
        return true;
    }

    static Map<?, ?> parseRawAppInstallation(BinaryDataSecret bds,
                                                     ObjectMapper mapper) {
        try { // find out if it's at least valid JSON.
            var base = mapper.readValue(bds.getData(), Map.class);
            if (base.containsKey("githubAppInstallation")) {
                return base;
            } else {
                // it's JSON, but not in our format
                return Map.of();
            }
        } catch (Exception e) {
            // invalid JSON, may be a plaintext token
            return null;
        }
    }

    static Optional<GitHubAppAuthConfig> parseAppInstallation(BinaryDataSecret bds,
                                                              ObjectMapper mapper) {
        Map<?, ?> base = parseRawAppInstallation(bds, mapper);

        if (base == null || !base.containsKey("githubAppInstallation")) {
            // it's either not JSON or not in our format
            return Optional.empty();
        }

        try { // great, now convert it to the expected structure
            return Optional.of(mapper.convertValue(base.get("githubAppInstallation"), GitHubAppAuthConfig.class));
        } catch (IllegalArgumentException e) {
            // doesn't match the expected structure
            throw new GitHubAppException("Invalid app installation definition.", e);
        }
    }

    static String extractOwnerAndRepo(GitHubAppAuthConfig auth, URI repo) throws RepoExtractionException {
        var port = (repo.getPort() == -1 ? "" : (":" + repo.getPort()));
        var path = (repo.getPath() == null ? "" : repo.getPath());
        var repoHostPortAndPath = repo.getHost() + port + path;

        var match = auth.urlPattern().matcher(repoHostPortAndPath);

        if (!match.matches()) {
            // at this point, this should only fail if the urlPattern is not
            // constructed correctly. We wouldn't get there if the pattern didn't
            // match the repo in the first place.
            throw new RepoExtractionException("Failed to parse owner and repository from path: " + repo.getPath());
        }

        var baseUrl = match.group("baseUrl");
        var relevantPath = repo.toString().replaceAll("^.*" + baseUrl + "/?", "")
                .replaceAll(repo.getQuery() != null ? "\\?" + repo.getQuery() : "", "")
                .replaceFirst("\\.git$", "");

        // parse out the owner/repo from the path
        var pathParts = Arrays.stream(relevantPath.split("/"))
                .filter(e -> !e.isBlank())
                .limit(2)
                .toList();

        if (pathParts.size() != 2) {
            throw new RepoExtractionException("Failed to parse owner and repository from path: " + repo.getPath());
        }

        return pathParts.get(0) + "/" + pathParts.get(1);
    }

    private Utils() {}

}
