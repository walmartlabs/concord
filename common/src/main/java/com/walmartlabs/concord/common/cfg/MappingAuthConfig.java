package com.walmartlabs.concord.common.cfg;

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

import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.regex.Pattern;

public interface MappingAuthConfig {

    /** Regex matching the host, optional port and path of a Git repository URL. */
    Pattern urlPattern();

    /**
     * Username to use for authentication with a provided token. Some services
     * (e.g. GitHub API for app installation) require a specific username. Others
     * (e.g. GitHub API for personal access tokens) accept just the token and no username
     */
    @Nullable
    String username();

    /**
     * For compatibility with a {@link MappingAuthConfig} instance, a URI must match the
     * {@link #urlPattern()} regex. The regex may match against the path to support
     * either a Git host behind a reverse proxy or restricting the auth to specific
     * org/repo patterns.
     * @return {@code true} if this provider can handle the given repo URI, {@code false} otherwise.
     */
    default boolean canHandle(URI repo) {
        var port = (repo.getPort() == -1 ? "" : (":" + repo.getPort()));
        var path = (repo.getPath() == null ? "" : repo.getPath());
        var repoHostPortAndPath = repo.getHost() + port + path;

        return repoHostPortAndPath.matches(urlPattern() + ".*");
    }

    static Pattern assertBaseUrlPattern(String pattern) {
        return pattern.endsWith(".*")
                ? Pattern.compile(pattern)
                : Pattern.compile(pattern + ".*");
    }

    @Value.Immutable
    @Value.Style(jdkOnly = true)
    interface OauthAuthConfig extends MappingAuthConfig {
        String token();

        static ImmutableOauthAuthConfig.Builder builder() {
            return ImmutableOauthAuthConfig.builder();
        }
    }

    @Value.Immutable
    @Value.Style(jdkOnly = true)
    interface ConcordServerAuthConfig extends MappingAuthConfig {
        static ImmutableConcordServerAuthConfig.Builder builder() {
            return ImmutableConcordServerAuthConfig.builder();
        }
    }

}
