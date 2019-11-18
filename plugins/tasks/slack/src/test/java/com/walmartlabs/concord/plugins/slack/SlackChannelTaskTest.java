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
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

@Ignore
public class SlackChannelTaskTest {

    private static final String TEST_CHANNEL_NAME = "testChannel201911142";
    private static final String TEST_GROUP_NAME = "testGroup201911142";
    private static final String INVALID_TEST_PROXY = "proxify.not-wal-mart.com";
    private static final String VALID_TEST_PROXY = "proxy.wal-mart.com";
    private static final int TEST_PROXY_PORT = 9080;
    private static final String TEST_API_ENV_VAR = "SLACK_TEST_API_TOKEN";

    private static final Logger log = LoggerFactory.getLogger(SlackChannelTaskTest.class);

    @Test
    public void testCreateAndArchiveChannel() throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put(SlackChannelTask.API_TOKEN_KEY, System.getenv(TEST_API_ENV_VAR));

        m.put(SlackChannelTask.ACTION_KEY, SlackChannelTask.Action.CREATE.toString());
        m.put(SlackChannelTask.CHANNEL_NAME_KEY, TEST_CHANNEL_NAME);

        Map<String, Object> slackCfg = new HashMap<>();
        slackCfg.put("proxyAddress", VALID_TEST_PROXY);
        slackCfg.put("proxyPort", TEST_PROXY_PORT);
        m.put("slackCfg", slackCfg);

        MockContext ctx = new MockContext(m);
        SlackChannelTask t = new SlackChannelTask();
        t.execute(ctx);

        // ---

        String channelId = (String) ctx.getVariable(SlackChannelTask.RESULT_KEY);

        m = new HashMap<>();
        m.put(SlackChannelTask.API_TOKEN_KEY, System.getenv(TEST_API_ENV_VAR));

        m.put(SlackChannelTask.ACTION_KEY, SlackChannelTask.Action.ARCHIVE.toString());
        m.put(SlackChannelTask.CHANNEL_ID_KEY, channelId);
        m.put("slackCfg", slackCfg);

        ctx = new MockContext(m);
        t = new SlackChannelTask();
        t.execute(ctx);
    }

    @Test
    public void testCreateAndArchiveGroup() throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put(SlackChannelTask.API_TOKEN_KEY, System.getenv(TEST_API_ENV_VAR));

        m.put(SlackChannelTask.ACTION_KEY, SlackChannelTask.Action.CREATEGROUP.toString());
        m.put(SlackChannelTask.CHANNEL_NAME_KEY, TEST_GROUP_NAME);

        Map<String, Object> slackCfg = new HashMap<>();
        slackCfg.put("proxyAddress", VALID_TEST_PROXY);
        slackCfg.put("proxyPort", TEST_PROXY_PORT);
        m.put("slackCfg", slackCfg);

        MockContext ctx = new MockContext(m);
        SlackChannelTask t = new SlackChannelTask();
        t.execute(ctx);

        // ---

        String channelId = (String) ctx.getVariable(SlackChannelTask.RESULT_KEY);

        m = new HashMap<>();
        m.put(SlackChannelTask.API_TOKEN_KEY, System.getenv(TEST_API_ENV_VAR));

        m.put(SlackChannelTask.ACTION_KEY, SlackChannelTask.Action.ARCHIVEGROUP.toString());
        m.put(SlackChannelTask.CHANNEL_ID_KEY, channelId);

        m.put("slackCfg", slackCfg);

        ctx = new MockContext(m);
        t = new SlackChannelTask();
        t.execute(ctx);
    }

    @Test
    public void testCreateChannelInvalidProxy() throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put(SlackChannelTask.API_TOKEN_KEY, System.getenv(TEST_API_ENV_VAR));

        m.put(SlackChannelTask.ACTION_KEY, SlackChannelTask.Action.CREATE.toString());
        m.put(SlackChannelTask.CHANNEL_NAME_KEY, TEST_CHANNEL_NAME);

        Map<String, Object> slackCfg = new HashMap<>();
        slackCfg.put("proxyAddress", INVALID_TEST_PROXY);
        slackCfg.put("proxyPort", TEST_PROXY_PORT);
        m.put("slackCfg", slackCfg);

        MockContext ctx = new MockContext(m);
        SlackChannelTask t = new SlackChannelTask();
        try {
            t.execute(ctx);
        } catch (IOException ie) {
            log.info("Create channel with invalid proxy throws IOException. Error: {}", ie.getMessage());
        }
    }

    @Test
    public void testArchiveChannelInvalidProxy() throws Exception {
        String channelId = "testchannel";

        Map<String, Object> m = new HashMap<>();
        m.put(SlackChannelTask.API_TOKEN_KEY, System.getenv(TEST_API_ENV_VAR));

        m.put(SlackChannelTask.ACTION_KEY, SlackChannelTask.Action.ARCHIVE.toString());
        m.put(SlackChannelTask.CHANNEL_ID_KEY, channelId);

        Map<String, Object> slackCfg = new HashMap<>();
        slackCfg.put("proxyAddress", INVALID_TEST_PROXY);
        slackCfg.put("proxyPort", TEST_PROXY_PORT);
        m.put("slackCfg", slackCfg);


        MockContext ctx = new MockContext(m);
        SlackChannelTask t = new SlackChannelTask();
        try {
            t.execute(ctx);
        } catch (IOException ie) {
            log.info("Archive channel with invalid proxy throws IOException. Error: {}", ie.getMessage());
        }
    }

    @Test
    public void testCreateGroupInvalidProxy() throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put(SlackChannelTask.API_TOKEN_KEY, System.getenv(TEST_API_ENV_VAR));

        m.put(SlackChannelTask.ACTION_KEY, SlackChannelTask.Action.CREATEGROUP.toString());
        m.put(SlackChannelTask.CHANNEL_NAME_KEY, TEST_GROUP_NAME);

        Map<String, Object> slackCfg = new HashMap<>();
        slackCfg.put("proxyAddress", INVALID_TEST_PROXY);
        slackCfg.put("proxyPort", TEST_PROXY_PORT);
        m.put("slackCfg", slackCfg);

        MockContext ctx = new MockContext(m);
        SlackChannelTask t = new SlackChannelTask();
        try {
            t.execute(ctx);
        } catch (IOException ie) {
            log.info("Create group with invalid proxy throws IOException. Error: {}", ie.getMessage());
        }
    }

    @Test
    public void testArchiveGroupInvalidProxy() throws Exception {
        String channelId = "testchannel";

        Map<String, Object> m = new HashMap<>();
        m.put(SlackChannelTask.API_TOKEN_KEY, System.getenv(TEST_API_ENV_VAR));

        m.put(SlackChannelTask.ACTION_KEY, SlackChannelTask.Action.ARCHIVEGROUP.toString());
        m.put(SlackChannelTask.CHANNEL_ID_KEY, channelId);

        Map<String, Object> slackCfg = new HashMap<>();
        slackCfg.put("proxyAddress", INVALID_TEST_PROXY);
        slackCfg.put("proxyPort", TEST_PROXY_PORT);
        m.put("slackCfg", slackCfg);


        MockContext ctx = new MockContext(m);
        SlackChannelTask t = new SlackChannelTask();
        try {
            t.execute(ctx);
        } catch (IOException ie) {
            log.info("Archive group with invalid proxy throws IOException. Error: {}", ie.getMessage());
        }
    }
}
