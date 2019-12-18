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

import java.util.HashMap;
import java.util.Map;

@Ignore
public class SlackTaskTest {

    private static final String VALID_TEST_PROXY = "proxy.wal-mart.com";
    private static final String INVALID_TEST_PROXY = "proxify.not-wal-mart.com";
    private static final int TEST_PROXY_PORT = 9080;
    private static final String TEST_API_ENV_VAR = "SLACK_TEST_API_TOKEN";
    private static final String TEST_CHANNEL = "#ibodrov-slack-tst";
    private static final String TEST_MESSAGE = "test";

    private static final Logger log = LoggerFactory.getLogger(SlackTaskTest.class);

    @Test
    public void testMessage() throws Exception {
        Map<String, Object> m = new HashMap<>();

        Map<String, Object> slackCfg = new HashMap<>();
        slackCfg.put("authToken", System.getenv(TEST_API_ENV_VAR));
//        slackCfg.put("proxyAddress", VALID_TEST_PROXY);
//        slackCfg.put("proxyPort", TEST_PROXY_PORT);
        m.put("slackCfg", slackCfg);
        m.put("channelId", TEST_CHANNEL);
        m.put("text", TEST_MESSAGE);

        MockContext ctx = new MockContext(m);
        SlackTask t = new SlackTask();
        t.execute(ctx);
        Map result = (Map) ctx.getVariable("result");
        assert (boolean) result.get("ok");
    }

    @Test
    public void testMessageInvalidProxyThrowErrors() throws Exception {
        Map<String, Object> m = new HashMap<>();

        Map<String, Object> slackCfg = new HashMap<>();
        slackCfg.put("authToken", System.getenv(TEST_API_ENV_VAR));
        slackCfg.put("proxyAddress", INVALID_TEST_PROXY);
        slackCfg.put("proxyPort", TEST_PROXY_PORT);
        m.put("slackCfg", slackCfg);
        m.put("channelId", TEST_CHANNEL);
        m.put("text", TEST_MESSAGE);

        MockContext ctx = new MockContext(m);
        SlackTask t = new SlackTask();
        try {
            t.execute(ctx);
        } catch (RuntimeException re) {
            log.info("Execution throws Runtime exception: {}", re.getMessage());
        }
    }

    @Test
    public void testMessageInvalidProxyIgnoreErrors() throws Exception {
        Map<String, Object> m = new HashMap<>();

        Map<String, Object> slackCfg = new HashMap<>();
        slackCfg.put("authToken", System.getenv(TEST_API_ENV_VAR));
        slackCfg.put("proxyAddress", INVALID_TEST_PROXY);
        slackCfg.put("proxyPort", TEST_PROXY_PORT);
        m.put("slackCfg", slackCfg);
        m.put("channelId", TEST_CHANNEL);
        m.put("text", TEST_MESSAGE);
        m.put("ignoreErrors", true);

        MockContext ctx = new MockContext(m);
        SlackTask t = new SlackTask();
        try {
            t.execute(ctx);
        } catch (RuntimeException re) {
            log.info("Execution throws Runtime exception: {}", re.getMessage());
        }
    }
}
