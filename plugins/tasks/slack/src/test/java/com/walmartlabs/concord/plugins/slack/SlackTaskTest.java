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

import static org.junit.Assert.fail;

@Ignore
public class SlackTaskTest {

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
