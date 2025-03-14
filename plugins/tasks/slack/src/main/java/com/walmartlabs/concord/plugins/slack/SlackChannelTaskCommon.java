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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;

import static com.walmartlabs.concord.plugins.slack.SlackChannelTaskParams.*;
import static com.walmartlabs.concord.plugins.slack.SlackClient.Response;

public class SlackChannelTaskCommon {

    private static final Logger log = LoggerFactory.getLogger(SlackChannelTaskCommon.class);

    public static final String SLACK_CHANNEL_ID_KEY = "slackChannelId";

    public Map<String, Object> execute(SlackChannelTaskParams in) throws Exception {
        log.debug("Starting '{}' action...", in.action());

        switch (in.action()) {
            case CREATE: {
                return createChannel((CreateChannelParams)in);
            }
            case CREATEGROUP: {
                return createGroup((CreateGroupParams)in);
            }
            case ARCHIVE: {
                archiveChannel((ArchiveChannelParams)in);
                break;
            }
            case ARCHIVEGROUP: {
                archiveGroup((ArchiveGroupParams)in);
                break;
            }
            default:
                throw new IllegalArgumentException("Unsupported action type: " + in.action());
        }
        return Collections.emptyMap();
    }

    private static Map<String, Object> createChannel(CreateChannelParams in) throws Exception {
        String channelName = in.channelName();

        SlackConfiguration cfg = SlackConfiguration.from(in.cfg());
        try (SlackClient client = new SlackClient(cfg)) {
            Response r = client.createChannel(channelName);
            handleError(in.action(), r, channelName);

            String channelId = Utils.extractString(r, "channel", "id");
            log.info("Slack channel created: {} -> {} (stored as '{}' variable)", channelName, channelId, SLACK_CHANNEL_ID_KEY);
            return Collections.singletonMap(SLACK_CHANNEL_ID_KEY, channelId);
        }
    }

    private static Map<String, Object> createGroup(CreateGroupParams in) throws Exception {
        String channelName = in.channelName();

        SlackConfiguration cfg = SlackConfiguration.from(in.cfg());
        try (SlackClient client = new SlackClient(cfg)) {
            Response r = client.createGroupChannel(channelName);
            handleError(in.action(), r, channelName);

            String channelId = Utils.extractString(r, "group", "id");
            log.info("Slack group created: {} -> {} (stored as '{}' variable)", channelName, channelId, SLACK_CHANNEL_ID_KEY);
            return Collections.singletonMap(SLACK_CHANNEL_ID_KEY, channelId);
        }
    }

    private static void archiveChannel(ArchiveChannelParams in) throws Exception {
        String channelId = in.channelId();

        SlackConfiguration cfg = SlackConfiguration.from(in.cfg());
        try (SlackClient client = new SlackClient(cfg)) {
            Response r = cfg.isLegacy()
                    ? client.archiveChannel(channelId)
                    : client.archiveConversation(channelId);
            handleError(in.action(), r, channelId);
        }
    }

    private static void archiveGroup(ArchiveGroupParams in) throws Exception {
        String channelId = in.channelId();

        SlackConfiguration cfg = SlackConfiguration.from(in.cfg());
        try (SlackClient client = new SlackClient(cfg)) {
            Response r = client.archiveGroup(channelId);
            handleError(in.action(), r, channelId);
        }
    }

    private static void handleError(Action action, Response r, String channelId) {
        if (!r.isOk()) {
            throw new RuntimeException(action + " error (channel: '" + channelId + "'): " + r.getError());
        }
    }
}
