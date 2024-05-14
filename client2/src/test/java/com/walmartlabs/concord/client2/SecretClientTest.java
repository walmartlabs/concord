package com.walmartlabs.concord.client2;

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

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.walmartlabs.concord.client2.impl.auth.ApiKey;
import com.walmartlabs.concord.common.secret.BinaryDataSecret;
import com.walmartlabs.concord.sdk.Constants;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WireMockTest
public class SecretClientTest {

    @Test
    public void testInvalidSecretType(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        String orgName = "org_" + System.currentTimeMillis();
        String secretName = "secret_" + System.currentTimeMillis();

        stubFor(post(urlEqualTo("/api/v1/org/" + orgName + "/secret/" + secretName + "/data"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(Constants.Headers.SECRET_TYPE, SecretEntryV2.TypeEnum.DATA.name())
                        .withBody("Hello!")));

        ApiClient apiClient = new DefaultApiClientFactory("http://localhost:" + wmRuntimeInfo.getHttpPort()).create();
        SecretClient secretClient = new SecretClient(apiClient);

        try {
            secretClient.getData(orgName, secretName, null, SecretEntryV2.TypeEnum.KEY_PAIR);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Unexpected type of " + orgName + "/" + secretName));
        }
    }

    @Test
    @Disabled
    public void testGetSecret() throws Exception {
        ApiClient apiClient = new DefaultApiClientFactory("http://localhost:8001").create();
        apiClient.setAuth(new ApiKey("cTFxMXExcTE"));
        SecretClient secretClient = new SecretClient(apiClient);

        BinaryDataSecret result = secretClient.getData("Default", "test", null, SecretEntryV2.TypeEnum.DATA);
        System.out.println(">>> '" + new String(result.getData()) + "'");
    }
}
