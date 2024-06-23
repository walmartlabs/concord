package com.walmartlabs.concord.it.server;

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

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.walmartlabs.concord.client2.ProcessEntry;
import com.walmartlabs.concord.client2.StartProcessResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForStatus;

@Disabled("needs a fix for new wiremock version")
public class DependencyManagerIT extends AbstractServerIT {

    @RegisterExtension
    static WireMockExtension rule = WireMockExtension.newInstance()
            .options(wireMockConfig()
                    .dynamicPort()
                    .globalTemplating(true)
                    .extensions(new HttpTaskIT.RequestHeaders()))
            .build();

    @BeforeAll
    public static void setUp() {
        rule.stubFor(get(urlEqualTo("/item.txt"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/octet-stream")
                        .withBody("Hello!".getBytes()))
        );
    }

    @AfterAll
    public static void tearDown() {
        rule.shutdownServer();
    }

    @Test
    public void test() throws Exception {
        byte[] payload = archive(DependencyManagerIT.class.getResource("dependencyManager").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);

        Map<String, Object> cfg = new HashMap<>();
        cfg.put("dependencies", new String[]{"mvn://com.walmartlabs.concord.it.tasks:dependency-manager-test:" + ITConstants.PROJECT_VERSION});
        String url = "http://" + env("IT_DOCKER_HOST_ADDR", "localhost") + ":" + rule.getPort() + "/item.txt";
        cfg.put("arguments", Collections.singletonMap("url", url));

        input.put("request", cfg);

        StartProcessResponse spr = start(input);

        // ---

        ProcessEntry pir = waitForStatus(getApiClient(), spr.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);
        byte[] ab = getLog(pir.getInstanceId());

        assertLog(".*downloading.*", ab);
        assertLog(".*using a cached copy.*", ab);
        assertLog(".*Got: Hello!.*", ab);
    }
}
