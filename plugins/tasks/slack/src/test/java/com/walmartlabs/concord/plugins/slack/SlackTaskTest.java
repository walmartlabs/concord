package com.walmartlabs.concord.plugins.slack;

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

import com.walmartlabs.concord.sdk.MockContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Disabled
public class SlackTaskTest {

    @BeforeEach
    public void setUp() {
        assumeTrue(TestParams.TEST_API_TOKEN != null);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testMessage() {
        Map<String, Object> m = new HashMap<>();

        Map<String, Object> slackCfg = new HashMap<>();
        slackCfg.put("authToken", TestParams.TEST_API_TOKEN);
        slackCfg.put("proxyAddress", TestParams.TEST_PROXY_ADDRESS);
        slackCfg.put("proxyPort", TestParams.TEST_PROXY_PORT);
        m.put("slackCfg", slackCfg);
        m.put("channelId", TestParams.TEST_CHANNEL);
        m.put("text", "test");

        MockContext ctx = new MockContext(m);
        SlackTask t = new SlackTask();
        t.execute(ctx);

        Map<String, Object> result = (Map<String, Object>) ctx.getVariable("result");
        assert (boolean) result.get("ok");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testJsonMessage() {
        Map<String, Object> m = new HashMap<>();

        Map<String, Object> slackCfg = new HashMap<>();
        slackCfg.put("authToken", TestParams.TEST_API_TOKEN);
        slackCfg.put("proxyAddress", TestParams.TEST_PROXY_ADDRESS);
        slackCfg.put("proxyPort", TestParams.TEST_PROXY_PORT);
        m.put("slackCfg", slackCfg);

        String json = "{\n" +
                "  \"channel\": \"@SLACK_CHANNEL@\",\n" +
                "  \"attachments\": [\n" +
                "    {\n" +
                "      \"mrkdwn_in\": [\n" +
                "        \"text\"\n" +
                "      ],\n" +
                "      \"color\": \"#36a64f\",\n" +
                "      \"author_name\": \"Jason van Zyl\",\n" +
                "      \"author_link\": \"https://github.com/jvanzyl\",\n" +
                "      \"author_icon\": \"https://github.com/jvanzyl.png\",\n" +
                "      \"title\": \"#1234 Add JSON support for Slack messages\",\n" +
                "      \"title_link\": \"https://github.com/jvanzyl/test/commit/44921371d769d85e7ad7665c36a802c2db47aee1\",\n" +
                "      \"text\": \"Modify all the dodos to make JSON slack messages work.\",\n" +
                "      \"fields\": [\n" +
                "        {\n" +
                "          \"title\": \":white_check_mark: *Unit Tests*\"\n" +
                "        },\n" +
                "        {\n" +
                "          \"title\": \":white_check_mark: *Integration Tests*\"\n" +
                "        }\n" +
                "      ],\n" +
                "      \"footer\": \"walmartlabs/concord\",\n" +
                "      \"footer_icon\": \"https://github.com/github.png\",\n" +
                "      \"ts\": \"now\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        json = json.replace("@SLACK_CHANNEL@", TestParams.TEST_CHANNEL);
        m.put("json", json);

        MockContext ctx = new MockContext(m);
        SlackTask t = new SlackTask();
        t.execute(ctx);

        Map<String, Object> result = (Map<String, Object>) ctx.getVariable("result");
        assert (boolean) result.get("ok");
    }

    @Test
    public void testMessageInvalidProxyThrowErrors() {
        Map<String, Object> m = new HashMap<>();

        Map<String, Object> slackCfg = new HashMap<>();
        slackCfg.put("authToken", TestParams.TEST_API_TOKEN);
        slackCfg.put("proxyAddress", TestParams.TEST_INVALID_PROXY_ADDRESS);
        slackCfg.put("proxyPort", TestParams.TEST_PROXY_PORT);
        m.put("slackCfg", slackCfg);
        m.put("channelId", TestParams.TEST_CHANNEL);
        m.put("text", "test");

        MockContext ctx = new MockContext(m);
        SlackTask t = new SlackTask();
        try {
            t.execute(ctx);
            fail("should fail");
        } catch (Exception e) {
            // expected
        }
    }

    @Test
    public void testMessageInvalidProxyIgnoreErrors() {
        Map<String, Object> m = new HashMap<>();

        Map<String, Object> slackCfg = new HashMap<>();
        slackCfg.put("authToken", TestParams.TEST_API_TOKEN);
        slackCfg.put("proxyAddress", TestParams.TEST_PROXY_ADDRESS);
        slackCfg.put("proxyPort", TestParams.TEST_PROXY_PORT);
        m.put("slackCfg", slackCfg);
        m.put("channelId", TestParams.TEST_CHANNEL);
        m.put("text", "test");
        m.put("ignoreErrors", true);

        MockContext ctx = new MockContext(m);
        SlackTask t = new SlackTask();
        t.execute(ctx);
    }
}
