package com.walmartlabs.concord.it.console;

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

import com.google.gson.reflect.TypeToken;
import com.squareup.okhttp.Call;
import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.ApiResponse;
import com.walmartlabs.concord.client.ClientUtils;
import com.walmartlabs.concord.client.ConcordApiClient;
import com.walmartlabs.concord.client.StartProcessResponse;
import com.walmartlabs.concord.it.common.ForbiddenException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.walmartlabs.concord.it.console.Utils.env;

public class ConcordServerRule implements TestRule {

    private static final Logger log = LoggerFactory.getLogger(ConcordServerRule.class);

    private final String baseUrl;

    private ApiClient client;

    public ConcordServerRule() {
        this.baseUrl = env("IT_SERVER_BASE_URL", "http://localhost:8001");
        log.info("Using baseUrl: {}", baseUrl);
    }

    public ApiClient getClient() {
        return client;
    }

    public StartProcessResponse start(Map<String, Object> input) throws ApiException {
        return request("/api/v1/process", input, StartProcessResponse.class);
    }

    public <T> T request(String uri, Map<String, Object> input, Class<T> entityType) throws ApiException {
        ApiResponse<T> resp = ClientUtils.postData(client, uri, input, entityType);

        int code = resp.getStatusCode();
        if (code < 200 || code >= 300) {
            if (code == 403) {
                throw new ForbiddenException("Forbidden!", resp.getData());
            }

            throw new ApiException("Request error: " + code);
        }

        return resp.getData();
    }

    public byte[] getLog(String logFileName) throws ApiException {
        Set<String> auths = client.getAuthentications().keySet();
        String[] authNames = auths.toArray(new String[0]);

        Call c = client.buildCall("/logs/" + logFileName, "GET", new ArrayList<>(), new ArrayList<>(),
                null, new HashMap<>(), new HashMap<>(), authNames, null);

        Type t = new TypeToken<byte[]>() {
        }.getType();
        return client.<byte[]>execute(c, t).getData();
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                setUp();
                base.evaluate();
            }
        };
    }

    private void setUp() {
        this.client = new ConcordApiClient(baseUrl)
                .setApiKey(Concord.ADMIN_API_KEY);
    }
}
