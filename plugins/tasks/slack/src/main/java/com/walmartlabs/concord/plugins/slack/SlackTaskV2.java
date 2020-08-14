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
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;

import javax.inject.Named;
import java.util.Collection;

@Named("slack")
public class SlackTaskV2 implements Task {

    @Override
    public TaskResult execute(Variables input) throws Exception {
        SlackConfiguration slackCfg = SlackConfiguration.from(input.toMap());

        String channelId = input.assertString(TaskParams.CHANNEL_ID.getKey());
        String text = input.getString(TaskParams.TEXT.getKey(), null);
        String ts = input.getString(TaskParams.TS.getKey(), null);
        boolean replyBroadcast = input.getBoolean(TaskParams.REPLY_BROADCAST.getKey(), false);
        String iconEmoji = input.getString(TaskParams.ICON_EMOJI.getKey(), null);
        String username = input.getString(TaskParams.USERNAME.getKey(), null);
        Collection<Object> attachments = input.getCollection(TaskParams.ATTACHMENTS.getKey(), null);

        SlackClient.Response r = Slack.sendMessage(slackCfg, channelId, ts, replyBroadcast, text, iconEmoji, username, attachments);
        return new TaskResult(r.isOk(), r.getError(), null)
                .value("id", Utils.extractString(r, "channel"))
                .value("ts", r.getTs());
    }
}
