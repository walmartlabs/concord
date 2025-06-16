package com.walmartlabs.concord.plugins.slack;

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
 * Unless required by applicable law or agreed to in writing, software
 *
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.walmartlabs.concord.runtime.v2.sdk.MapBackedVariables;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Disabled
public class SlackClientTest {

    @BeforeEach
    public void setUp() {
        assumeTrue(TestParams.TEST_API_TOKEN != null);
    }

    @Test
    public void validateSlackClientPostingMessageWithJson() throws Exception {

        Map<String, Object> cfgMap = new HashMap<>();
        cfgMap.put("apiToken", TestParams.TEST_API_TOKEN);
        SlackClient client = new SlackClient(SlackConfiguration.from(new SlackConfigurationParams(new MapBackedVariables(cfgMap))));

        String postJson = "{\n" +
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
                "          \"title\": \":hourglass_flowing_sand: *Unit Tests*\"\n" +
                "        },\n" +
                "        {\n" +
                "          \"title\": \":hourglass_flowing_sand: *Integration Tests*\"\n" +
                "        }\n" +
                "      ],\n" +
                "      \"footer\": \"walmartlabs/concord\",\n" +
                "      \"footer_icon\": \"https://github.com/github.png\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        postJson = postJson.replace("@SLACK_CHANNEL@", TestParams.TEST_CHANNEL);
        SlackClient.Response postResponse = client.postJsonMessage(postJson);
        assertTrue(postResponse.isOk());

        Thread.sleep(2000);
        String ts = postResponse.getTs();
        String updateJson = "{\n" +
                "  \"channel\": \"@SLACK_CHANNEL@\",\n" +
                "  \"ts\": \"@TS@\",\n" +
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
                "      \"footer_icon\": \"https://github.com/github.png\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        updateJson = updateJson.replace("@TS@", ts);
        updateJson = updateJson.replace("@SLACK_CHANNEL@", TestParams.TEST_CHANNEL);
        System.out.println(updateJson);
        SlackClient.Response updateResponse = client.updateJsonMessage(updateJson);
        assertTrue(updateResponse.isOk());
    }

    @Test
    public void validateSlackClientPostingMessageWithParameters() throws Exception {

        Map<String, Object> cfgMap = new HashMap<>();
        cfgMap.put("apiToken", TestParams.TEST_API_TOKEN);
        SlackClient client = new SlackClient(SlackConfiguration.from(new SlackConfigurationParams(new MapBackedVariables(cfgMap))));

        String channelId = TestParams.TEST_CHANNEL;
        SlackClient.Response response = client.message(channelId, null, false, "Hello from Concord!", null, null, null, null);
        assertTrue(response.isOk());
    }
}
