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

import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.sdk.Context;

public interface ApiClientFactory {

    /**
     * @deprecated use {@link #create(ApiClientConfiguration)}
     */
    @Deprecated
    default ApiClient create(String sessionToken) {
        return create(ApiClientConfiguration.builder()
                .sessionToken(sessionToken)
                .build());
    }

    /**
     * @deprecated use {@link #create(ApiClientConfiguration)}
     */
    @Deprecated
    default ApiClient create(Context ctx) {
        return create(ApiClientConfiguration.builder()
                .context(ctx)
                .build());
    }

    ApiClient create(ApiClientConfiguration cfg);
}

