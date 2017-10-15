package com.walmartlabs.concord.server.mock;

import com.github.tomakehurst.wiremock.WireMockServer;
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
                .willReturn(aResponse()
                        .okForJson(response)
                        .withFixedDelay(1000)));
    }

    private static void mockProcessKill(WireMockServer mock) {
        mock.stubFor(delete(urlPathMatching("/api/v1/process/.*"))
                .willReturn(aResponse()
                        .withFixedDelay(2000)));
    }
}
