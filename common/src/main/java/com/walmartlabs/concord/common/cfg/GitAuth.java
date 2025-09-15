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

import java.net.URI;

public interface GitAuth {

    /** Regex matching the host, optional port and path of a Git repository URL. */
    String baseUrl();

    /**
     * For compatibility with a {@link GitAuth} instance, a URI must match the
     * {@link #baseUrl()} regex. The regex may match against the path to support
     * either a Git host behind a reverse proxy or restricting the auth to specific
     * org/repo patterns.
     * @return {@code true} if this provider can handle the given repo URI, {@code false} otherwise.
     */
    default boolean canHandle(URI repo) {
        String repoHostPortAndPath = repo.getHost() + (repo.getPort() == -1 ? "" : (":" + repo.getPort())) + (repo.getPath() == null ? "" : repo.getPath());

        return repoHostPortAndPath.matches(baseUrl() + ".*");
    }

    @Value.Immutable
    @Value.Style(jdkOnly = true)
    interface Oauth extends GitAuth {
        String token();

        static ImmutableOauth.Builder builder() {
            return ImmutableOauth.builder();
        }
    }

    @Value.Immutable
    @Value.Style(jdkOnly = true)
    interface ConcordServer extends GitAuth {
        // TODO rename to ConcordServerAuth?
        static ImmutableConcordServer.Builder builder() {
            return ImmutableConcordServer.builder();
        }
    }

}
