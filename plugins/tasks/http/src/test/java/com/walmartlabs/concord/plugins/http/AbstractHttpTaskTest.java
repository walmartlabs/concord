package com.walmartlabs.concord.plugins.http;

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

import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.walmartlabs.concord.sdk.Context;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentMatchers;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyObject;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

public abstract class AbstractHttpTaskTest {
    @Rule
    public WireMockRule rule = new WireMockRule(WireMockConfiguration.options().notifier(new ConsoleNotifier(true)));

    @Rule
    public TemporaryFolder folder = new TemporaryFolder(new File(System.getProperty("user.dir") + "/src/test/resources/__files"));

    protected HttpTask task;
    /**
     * This will be use to hold the response when {@link HttpTask} calls the {@link Context#setVariable(String, Object)}
     */
    protected Map<String, Object> response;

    @Before
    public void setup() {
        task = new HttpTask();
        stubForJsonResponse();
        stubForStringResponse();
        stubForPostRequest();
        stubForGetSecureEndpoint();
        stubForPostSecureEndpoint();
        stubForPostRequestForRequestTypeFile();
        stubForGetRequestForResponseTypeStringFile();
        stubForGetRequestForResponseTypeJSONFile();
        stubForUnsuccessfulResponse();

    }

    @After
    public void tearDown() {
        response = null;
    }

    protected void initCxtForRequest(Context ctx, String requestMethod, String requestType, String responseType, String url) {
        when(ctx.getVariable("url")).thenReturn(url);
        when(ctx.getVariable("method")).thenReturn(requestMethod);
        when(ctx.getVariable("request")).thenReturn(requestType);
        when(ctx.getVariable("response")).thenReturn(responseType);
        when(ctx.getVariable("out")).thenReturn("rsp");
        doAnswer((Answer<Void>) invocation -> {
            response = (Map<String, Object>) invocation.getArguments()[1];
            return null;
        }).when(ctx).setVariable(anyString(), any());
    }

    protected void stubForJsonResponse() {
        rule.stubFor(get(urlEqualTo("/json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Accept", "application/json")
                        .withBody("[\n" +
                                "    {\n" +
                                "        \"id\": 1,\n" +
                                "        \"version\": \"1.0\"\n" +
                                "    },\n" +
                                "    {\n" +
                                "        \"id\": 2,\n" +
                                "        \"test\": \"1.1\"\n" +
                                "    }\n" +
                                "]"))
        );
    }

    protected void stubForStringResponse() {
        rule.stubFor(get(urlEqualTo("/string"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withHeader("Accept", "text/plain")
                        .withBody("Test"))
        );
    }

    protected void stubForPostRequest() {
        rule.stubFor(post(urlEqualTo("/post"))
                //.withRequestBody(matchingJsonPath("$.request"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Accept", "application/json")
                        .withBody("{\n" +
                                "  \"message\": \"Success\"\n" +
                                "}"))
        );
    }

    protected void stubForGetSecureEndpoint() {
        rule.stubFor(get(urlEqualTo("/secure"))
                .withBasicAuth("cn=test", "password")
                .willReturn(aResponse()
                        .withStatus(401)
                        .withBody("Unauthorized"))
        );
    }

    protected void stubForPostSecureEndpoint() {
        rule.stubFor(post(urlEqualTo("/secure"))
                .withBasicAuth("cn=test", "password")
                .willReturn(aResponse()
                        .withStatus(201)
                        .withBody("Success response"))
        );
    }

    protected void stubForPostRequestForRequestTypeFile() {
        rule.stubFor(post(urlEqualTo("/file"))
                .withHeader("Content-Type", equalTo("application/octet-stream"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"testObject\": {\n" +
                                "    \"testString\": \"hello\",\n" +
                                "    \"testInteger\": \"2\"\n" +
                                "  }\n" +
                                "}"))
        );
    }


    protected void stubForGetRequestForResponseTypeStringFile() {
        rule.stubFor(get(urlEqualTo("/stringFile"))
                .withHeader("Accept", equalTo("*/*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/octet-stream")
                        .withBodyFile("file.bin"))
        );
    }

    protected void stubForGetRequestForResponseTypeJSONFile() {
        rule.stubFor(get(urlEqualTo("/JSONFile"))
                .withHeader("Accept", equalTo("*/*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/octet-stream")
                        .withBodyFile("jsonFile.bin"))
        );
    }


    protected void stubForUnsuccessfulResponse() {
        rule.stubFor(get(urlEqualTo("/unsuccessful"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withBody("Error string"))
        );
    }

}
