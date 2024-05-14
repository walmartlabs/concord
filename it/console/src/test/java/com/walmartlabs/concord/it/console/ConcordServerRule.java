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

import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.it.common.JGitUtils;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.it.console.Utils.env;

public class ConcordServerRule implements BeforeEachCallback {

    private static final Logger log = LoggerFactory.getLogger(ConcordServerRule.class);

    private final String baseUrl;

    private ApiClient client;

    public ConcordServerRule() {
        this.baseUrl = env("IT_SERVER_BASE_URL", "http://localhost:8001");
        log.info("Using baseUrl: {}", baseUrl);

        JGitUtils.applyWorkarounds();
    }

    public ApiClient getClient() {
        return client;
    }

    public StartProcessResponse start(Map<String, Object> input) throws ApiException {
        return new ProcessApi(client).startProcess(input);
    }

    public byte[] getLog(UUID instanceId) throws ApiException {
        try (InputStream is = new ProcessApi(client).getProcessLog(instanceId, null)) {
            return is.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        setUp();
    }

    private void setUp() {
        this.client = new DefaultApiClientFactory(baseUrl)
                .create(ApiClientConfiguration.builder().apiKey(Concord.ADMIN_API_KEY).build());
    }
}
