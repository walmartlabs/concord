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
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assume.assumeTrue;

public class SlackChannelTaskTest {

    @Before
    public void setUp() {
        assumeTrue(TestParams.TEST_API_TOKEN != null);
        assumeTrue(TestParams.TEST_USER_API_TOKEN != null);
    }

    @Test
    public void testCreateAndArchiveChannel() throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put(TaskParams.API_TOKEN.getKey(), TestParams.TEST_API_TOKEN);

        m.put(TaskParams.ACTION.getKey(), SlackChannelTask.Action.CREATE.toString());
        m.put(TaskParams.CHANNEL_NAME.getKey(), TestParams.TEST_CHANNEL + System.currentTimeMillis());

        Map<String, Object> slackCfg = new HashMap<>();
        slackCfg.put(TaskParams.PROXY_ADDRESS.getKey(), TestParams.TEST_PROXY_ADDRESS);
        slackCfg.put(TaskParams.PROXY_PORT.getKey(), TestParams.TEST_PROXY_PORT);
        m.put("slackCfg", slackCfg);

        MockContext ctx = new MockContext(m);
        SlackChannelTask t = new SlackChannelTask();
        t.execute(ctx);

        // ---

        String channelId = (String) ctx.getVariable(SlackChannelTask.SLACK_CHANNEL_ID_KEY);

        m = new HashMap<>();
        m.put(TaskParams.API_TOKEN.getKey(), TestParams.TEST_API_TOKEN);

        m.put(TaskParams.ACTION.getKey(), SlackChannelTask.Action.ARCHIVE.toString());
        m.put(TaskParams.CHANNEL_ID.getKey(), channelId);
        m.put("slackCfg", slackCfg);

        ctx = new MockContext(m);
        t = new SlackChannelTask();
        t.execute(ctx);
    }

    @Test
    public void testCreateAndArchiveGroup() throws Exception {
        //
        // Managing groups is specifically requires a real user token. Bots cannot manage
        // groups even though the Slack UI lets you add the groups.write permission for a bot.
        // A bot can't actually perform any action granted by the groups.write permission so
        // you need to make sure you are using the token for the OAuth user and not the bot. You
        // can distinguish between as follows:
        //
        // Bot Tokens start with xoxb-
        // User Tokens start with xoxp-
        //
        Map<String, Object> m = new HashMap<>();
        m.put(TaskParams.API_TOKEN.getKey(), TestParams.TEST_USER_API_TOKEN);

        m.put(TaskParams.ACTION.getKey(), SlackChannelTask.Action.CREATEGROUP.toString());
        m.put(TaskParams.CHANNEL_NAME.getKey(), TestParams.TEST_CHANNEL + System.currentTimeMillis());

        Map<String, Object> slackCfg = new HashMap<>();
        slackCfg.put(TaskParams.PROXY_ADDRESS.getKey(), TestParams.TEST_PROXY_ADDRESS);
        slackCfg.put(TaskParams.PROXY_PORT.getKey(), TestParams.TEST_PROXY_PORT);
        m.put("slackCfg", slackCfg);

        MockContext ctx = new MockContext(m);
        SlackChannelTask t = new SlackChannelTask();
        t.execute(ctx);

        // ---

        String channelId = (String) ctx.getVariable(SlackChannelTask.SLACK_CHANNEL_ID_KEY);

        m = new HashMap<>();
        m.put(TaskParams.API_TOKEN.getKey(), TestParams.TEST_USER_API_TOKEN);

        m.put(TaskParams.ACTION.getKey(), SlackChannelTask.Action.ARCHIVEGROUP.toString());
        m.put(TaskParams.CHANNEL_ID.getKey(), channelId);

        m.put("slackCfg", slackCfg);

        ctx = new MockContext(m);
        t = new SlackChannelTask();
        t.execute(ctx);
    }
}
