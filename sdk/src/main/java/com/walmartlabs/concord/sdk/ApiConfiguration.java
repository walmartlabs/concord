package com.walmartlabs.concord.sdk;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

/**
 * Provides basic configuration details of Concord API.
 * Can be injected into a task plugin using {@code @Inject}.
 */
public interface ApiConfiguration {

    /**
     * @return the base URL of the API, e.g. https://concord.example.com/
     */
    String getBaseUrl();

    /**
     * @return connection timeout (ms)
     */
    int connectTimeout();

    /**
     * @return socket read timeout (ms)
     */
    int readTimeout();

    /**
     * @deprecated
     * @return the current session token that can be used to talk to the API.
     * @apiNote can still be used for compatibility with v1 tasks while running in the runtime v1.
     */
    @Deprecated
    String getSessionToken(Context ctx);
}
