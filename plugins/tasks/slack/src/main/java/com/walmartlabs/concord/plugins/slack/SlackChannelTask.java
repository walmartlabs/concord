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

import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.ContextUtils;
import com.walmartlabs.concord.sdk.MapUtils;
import com.walmartlabs.concord.sdk.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.util.Map;

import static com.walmartlabs.concord.plugins.slack.SlackClient.Response;

@Named("slackChannel")
public class SlackChannelTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(SlackChannelTask.class);

    public static final String SLACK_CHANNEL_ID_KEY = "slackChannelId";

    @Override
    public void execute(Context ctx) throws Exception {
        Action action = getAction(ctx);
        log.info("Starting '{}' action...", action);

        Map<String, Object> args = Utils.collectAgs(ctx);

        switch (action) {
            case CREATE: {
                createChannel(ctx, args);
                break;
            }
            case CREATEGROUP: {
                createGroup(ctx, args);
                break;
            }
            case ARCHIVE: {
                archiveChannel(ctx, args);
                break;
            }
            case ARCHIVEGROUP: {
                archiveGroup(ctx, args);
                break;
            }
            default:
                throw new IllegalArgumentException("Unsupported action type: " + action);
        }
    }

    private static void createChannel(Context ctx, Map<String, Object> args) throws Exception {
        String channelName = MapUtils.assertString(args, TaskParams.CHANNEL_NAME.getKey());

        SlackConfiguration cfg = SlackConfiguration.from(args);
        try (SlackClient client = new SlackClient(cfg)) {
            Response r = client.createChannel(channelName);
            handleError(ctx, r, channelName);

            String channelId = Utils.extractString(r, "channel", "id");
            ctx.setVariable(SLACK_CHANNEL_ID_KEY, channelId);
            log.info("Slack channel created: {} -> {} (stored as '{}' variable)", channelName, channelId, SLACK_CHANNEL_ID_KEY);
        }
    }

    private static void createGroup(Context ctx, Map<String, Object> args) throws Exception {
        String channelName = MapUtils.assertString(args, TaskParams.CHANNEL_NAME.getKey());

        SlackConfiguration cfg = SlackConfiguration.from(args);
        try (SlackClient client = new SlackClient(cfg)) {
            Response r = client.createGroupChannel(channelName);
            handleError(ctx, r, channelName);

            String channelId = Utils.extractString(r, "group", "id");
            ctx.setVariable(SLACK_CHANNEL_ID_KEY, channelId);
            log.info("Slack group created: {} -> {} (stored as '{}' variable)", channelName, channelId, SLACK_CHANNEL_ID_KEY);
        }
    }

    private static void archiveChannel(Context ctx, Map<String, Object> args) throws Exception {
        String channelId = MapUtils.assertString(args, TaskParams.CHANNEL_ID.getKey());

        SlackConfiguration cfg = SlackConfiguration.from(args);
        try (SlackClient client = new SlackClient(cfg)) {
            Response r = client.archiveChannel(channelId);
            handleError(ctx, r, channelId);
        }
    }

    private static void archiveGroup(Context ctx, Map<String, Object> args) throws Exception {
        String channelId = MapUtils.assertString(args, TaskParams.CHANNEL_ID.getKey());

        SlackConfiguration cfg = SlackConfiguration.from(args);
        try (SlackClient client = new SlackClient(cfg)) {
            Response r = client.archiveGroup(channelId);
            handleError(ctx, r, channelId);
        }
    }

    private static void handleError(Context ctx, Response r, String channelId) {
        Action action = getAction(ctx);
        if (!r.isOk()) {
            throw new RuntimeException(action + " error (channel: '" + channelId + "): " + r.getError());
        }
    }

    private static Action getAction(Context ctx) {
        String s = ContextUtils.assertString(ctx, TaskParams.ACTION.getKey());
        return Action.valueOf(s.toUpperCase());
    }

    public enum Action {
        CREATE,
        CREATEGROUP,
        ARCHIVE,
        ARCHIVEGROUP
    }
}
