package com.walmartlabs.concord.agent;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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

import com.squareup.okhttp.Call;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.ApiClient;
import com.walmartlabs.concord.server.ApiException;
import com.walmartlabs.concord.server.ApiResponse;
import com.walmartlabs.concord.server.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

public abstract class AbstractClient {

    private static final Logger log = LoggerFactory.getLogger(AbstractClient.class);

    private final ApiClient client;

    private final int retryCount;
    private final long retryInterval;

    public AbstractClient(Configuration cfg) throws IOException {
        this.client = createClient(cfg);

        this.retryCount = cfg.getRetryCount();
        this.retryInterval = cfg.getRetryInterval();
    }

    protected ApiClient getClient() {
        return client;
    }

    private static ApiClient createClient(Configuration cfg) throws IOException {
        ApiClient client = new ApiClient();
        client.setTempFolderPath(IOUtils.createTempDir("agent-client").toString());
        client.setBasePath(cfg.getServerApiBaseUrl());
        client.setApiKey(cfg.getApiKey());
        client.setReadTimeout(cfg.getReadTimeout());
        client.setConnectTimeout(cfg.getConnectTimeout());
        return client;
    }

    protected void postData(String path, Object data) throws ApiException {
        Map<String, String> headerParams = new HashMap<>();
        headerParams.put("Content-Type", MediaType.APPLICATION_OCTET_STREAM);

        String[] authNames = new String[] { "api_key", "ldap" };

        withRetry((Callable<Object>) () -> {
            Call c = client.buildCall(path, "POST", new ArrayList<>(), new ArrayList<Pair>(),
                    data, headerParams, new HashMap<>(), authNames, null);
            return client.execute(c);
        });
    }

    protected String getHeader(String name, ApiResponse<File> resp) {
        return resp.getHeaders().get(name).get(0);
    }

    protected <T> T withRetry(Callable<T> c) throws ApiException {
        Exception exception = null;
        int tryCount = 0;
        while (!Thread.currentThread().isInterrupted() && tryCount < retryCount + 1) {
            try {
                return c.call();
            } catch (Exception e) {
                log.error("call error, retry after {} sec", retryInterval / 1000, e);
                exception = e;
                sleep(retryInterval);
            }
            tryCount++;
        }

        throw new ApiException(exception);
    }

    protected static void sleep(long t) {
        try {
            Thread.sleep(t);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
