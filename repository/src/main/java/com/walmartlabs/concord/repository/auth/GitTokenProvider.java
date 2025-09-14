package com.walmartlabs.concord.repository.auth;

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

import com.walmartlabs.concord.sdk.Secret;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.Optional;

public interface GitTokenProvider {

    /**
     * @return {@code true} if this the given repo URI and secret are compatible
     *         with this provider's {@link #getAccessToken(String, URI, Secret)} method,
     *         {@code false} otherwise.
     */
    boolean canHandle(URI repo, @Nullable Secret secret);

    Optional<ActiveAccessToken> getAccessToken(String gitHost, URI repo, @Nullable Secret secret);

}
