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

import com.walmartlabs.concord.sdk.Secret;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.nio.file.Path;

@Value.Immutable
@Value.Style(jdkOnly = true)
public interface FetchRequest {

    /**
     * URL of GIT repository.
     */
    String url();

    /**
     * Target directory.
     */
    Path destination();

    /**
     * Concord secret for authentication.
     * If not provided {@link GitClientConfiguration#oauthToken()} is used for {@code https://} URLs.
     */
    @Nullable
    Secret secret();

    /**
     * Branch or Tag to checkout.
     */
    @Nullable
    String branchOrTag();

    /**
     * Commit ID to checkout.
     */
    @Nullable
    String commitId();

    /**
     * If {@code true} use shallow cloning.
     */
    @Value.Default
    default boolean shallow() {
        return true;
    }

    /**
     * Fetch the repository's submodules if {@code true}.
     */
    @Value.Default
    default boolean includeSubmodules() {
        return true;
    }

    /**
     * Get latest commit message and author if {@code true}.
     */
    @Value.Default
    default boolean withCommitInfo() {
        return false;
    }

    static ImmutableFetchRequest.Builder builder() {
        return ImmutableFetchRequest.builder();
    }
}
