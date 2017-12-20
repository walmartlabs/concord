package com.walmartlabs.concord.server.mock;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.walmartlabs.concord.server.console.UserResponse;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

public class Main {

    public static void main(String[] args) throws Exception {
        WireMockServer mock = new WireMockServer(options().port(8001));

        mockWhoAmI(mock);
        mockProcessKill(mock);

        mock.start();
    }

    private static void mockWhoAmI(WireMockServer mock) {
        UserResponse response = new UserResponse("ldap", "test", "Mock User", null);

        mock.stubFor(get(urlPathEqualTo("/api/service/console/whoami"))
                .willReturn(ResponseDefinitionBuilder
                        .okForJson(response)
                        .withFixedDelay(1000)));
    }

    private static void mockProcessKill(WireMockServer mock) {
        mock.stubFor(delete(urlPathMatching("/api/v1/process/.*"))
                .willReturn(aResponse()
                        .withFixedDelay(2000)));
    }
}
