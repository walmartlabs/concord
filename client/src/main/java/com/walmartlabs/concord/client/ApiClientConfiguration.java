package com.walmartlabs.concord.client;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.sdk.Context;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.UUID;

@Value.Immutable
@Value.Style(jdkOnly = true)
public interface ApiClientConfiguration {

    /**
     * Base URL of the API, e.g. {@code http://localhost:8001}
     */
    @Nullable
    String baseUrl();

    /**
     * The process' session token.
     * @see {@link com.walmartlabs.concord.sdk.ApiConfiguration#getSessionToken(Context)}
     */
    @Nullable
    String sessionToken();

    /**
     * The user's API key. If set then the session token will be ignored.
     */
    @Nullable
    String apiKey();

    /**
     * Current process' context. If {@link #sessionToken()} or {@link #apiKey()} both are
     * omitted, then the context can be used to extract the current session token.
     */
    @Nullable
    @Deprecated
    Context context();

    /**
     * Current process instanceId.
     */
    @Nullable
    @Deprecated
    UUID txId();

    static ImmutableApiClientConfiguration.Builder builder() {
        return ImmutableApiClientConfiguration.builder();
    }
}
