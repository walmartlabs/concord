package com.walmartlabs.concord.client;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.MockContext;
import org.junit.Ignore;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

@Ignore
public class SecretsTaskTest {

    @Test
    public void testUpdate() throws Exception {
        SecretsTask t = new SecretsTask(new ApiClientFactory() {
            @Override
            public ApiClient create(Context ctx) {
                return new ConcordApiClient("http://localhost:8001")
                        .setApiKey("auBy4eDWrKWsyhiDp3AQiw");
            }

            @Override
            public ApiClient create(ApiClientConfiguration cfg) {
                return null;
            }
        });

        Map<String, Object> args = new HashMap<>();
        args.put(Constants.Multipart.ORG_NAME, "Default");
        args.put(Constants.Multipart.NAME, "test");
        args.put(Constants.Multipart.STORE_PASSWORD, "AAAbbbCCC");
        args.put(SecretsTask.ACTION_KEY, "update");
        args.put(Constants.Multipart.DATA, "hello!");
        args.put(SecretsTask.NEW_STORE_PASSWORD_KEY, "CCCbbbAAA");

        MockContext ctx = new MockContext(args);
        t.execute(ctx);
    }
}
