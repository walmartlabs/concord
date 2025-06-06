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

import com.walmartlabs.concord.plugins.slack.SlackClient.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

public class Slack {

    private static final Logger log = LoggerFactory.getLogger(Slack.class);

    public static Response sendMessage(SlackConfiguration slackCfg,
                                       String channelId,
                                       String ts,
                                       boolean replyBroadcast,
                                       String text,
                                       String iconEmoji,
                                       String username,
                                       Collection<Object> attachments,
                                       Collection<Object> blocks) throws Exception {

        try (SlackClient client = new SlackClient(slackCfg)) {
            Response r = client.message(channelId, ts, replyBroadcast, text, iconEmoji, username, attachments, blocks);
            if (!r.isOk()) {
                log.warn("Error sending a Slack message: {}", r.getError());
            } else {
                log.info("Slack message sent into '{}' channel", channelId);
            }

            return r;
        }
    }

    private Slack() {
    }
}
