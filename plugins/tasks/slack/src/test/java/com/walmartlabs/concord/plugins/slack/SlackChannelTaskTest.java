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

import java.util.HashMap;
import java.util.Map;

@Ignore
public class SlackChannelTaskTest {

    @Test
    public void test() throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put(SlackChannelTask.API_TOKEN_KEY, System.getenv("SLACK_TEST_API_TOKEN"));

        m.put(SlackChannelTask.ACTION_KEY, SlackChannelTask.Action.CREATE.toString());
        m.put(SlackChannelTask.CHANNEL_NAME_KEY, "ibodrov-test07");

        MockContext ctx = new MockContext(m);
        SlackChannelTask t = new SlackChannelTask();
        t.execute(ctx);

        // ---

        String channelId = (String) ctx.getVariable(SlackChannelTask.RESULT_KEY);

        m = new HashMap<>();
        m.put(SlackChannelTask.API_TOKEN_KEY, System.getenv("SLACK_TEST_API_TOKEN"));

        m.put(SlackChannelTask.ACTION_KEY, SlackChannelTask.Action.ARCHIVE.toString());
        m.put(SlackChannelTask.CHANNEL_ID_KEY, channelId);

        ctx = new MockContext(m);
        t = new SlackChannelTask();
        t.execute(ctx);

        // ---

        m = new HashMap<>();
        m.put(SlackChannelTask.API_TOKEN_KEY, System.getenv("SLACK_TEST_API_TOKEN"));

        m.put(SlackChannelTask.ACTION_KEY, SlackChannelTask.Action.CREATEGROUP.toString());
        m.put(SlackChannelTask.CHANNEL_NAME_KEY, "ibodrov-test08");

        ctx = new MockContext(m);
        t = new SlackChannelTask();
        t.execute(ctx);

        // ---

        channelId = (String) ctx.getVariable(SlackChannelTask.RESULT_KEY);

        m = new HashMap<>();
        m.put(SlackChannelTask.API_TOKEN_KEY, System.getenv("SLACK_TEST_API_TOKEN"));

        m.put(SlackChannelTask.ACTION_KEY, SlackChannelTask.Action.ARCHIVEGROUP.toString());
        m.put(SlackChannelTask.CHANNEL_ID_KEY, channelId);

        ctx = new MockContext(m);
        t = new SlackChannelTask();
        t.execute(ctx);
    }
}
