package com.walmartlabs.concord.client;

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

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.sdk.Constants;
import org.junit.Rule;
import org.junit.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.assertTrue;

public class SecretClientTest {

    @Rule
    public WireMockRule wireMock = new WireMockRule(WireMockConfiguration.options()
            .dynamicPort());

    @Test
    public void testInvalidSecretType() throws Exception {
        String orgName = "org_" + System.currentTimeMillis();
        String secretName = "secret_" + System.currentTimeMillis();

        wireMock.stubFor(post(urlEqualTo("/api/v1/org/" + orgName + "/secret/" + secretName + "/data"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(Constants.Headers.SECRET_TYPE, SecretEntry.TypeEnum.DATA.name())
                        .withBody("Hello!")));

        ApiClient apiClient = new ConcordApiClient("http://localhost:" + wireMock.port());
        SecretClient secretClient = new SecretClient(apiClient);

        try {
            secretClient.getData(orgName, secretName, null, SecretEntry.TypeEnum.KEY_PAIR);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Unexpected type of " + orgName + "/" + secretName));
        }
    }
}
