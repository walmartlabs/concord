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
import com.walmartlabs.concord.sdk.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.util.Arrays;
import java.util.Map;

import static com.walmartlabs.concord.plugins.slack.SlackClient.Response;
import static com.walmartlabs.concord.plugins.slack.Utils.assertString;

@Named("slackChannel")
public class SlackChannelTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(SlackChannelTask.class);

    public static final String CHANNEL_ID_KEY = "channelId";
    public static final String CHANNEL_NAME_KEY = "channelName";
    public static final String ACTION_KEY = "action";
    public static final String API_TOKEN_KEY = "apiToken";
    public static final String RESULT_KEY = "slackChannelId";

    @Override
    public void execute(Context ctx) throws Exception {
        Action action = getAction(ctx);
        log.info("Starting '{}' action...", action);

        switch (action) {
            case CREATE: {
                createChannel(ctx);
                break;
            }
            case CREATEGROUP: {
                createGroup(ctx);
                break;
            }
            case ARCHIVE: {
                archiveChannel(ctx);
                break;
            }
            case ARCHIVEGROUP: {
                archiveGroup(ctx);
                break;
            }
            default:
                throw new IllegalArgumentException("Unsupported action type: " + action);
        }
    }

    private static void createChannel(Context ctx) throws Exception {
        String channelName = assertString(ctx, CHANNEL_NAME_KEY);
        String apiToken = assertString(ctx, API_TOKEN_KEY);

        SlackConfiguration cfg = SlackConfiguration.from(ctx, apiToken);
        try (SlackClient client = new SlackClient(cfg)) {
            Response r = client.createChannel(channelName);
            handleError(ctx, r, channelName);

            String channelId = extractString(r, "channel", "id");
            ctx.setVariable(RESULT_KEY, channelId);
            log.info("Slack channel created: {} -> {} (stored as '{}' variable)", channelName, channelId, RESULT_KEY);
        }
    }

    private static void createGroup(Context ctx) throws Exception {
        String channelName = assertString(ctx, CHANNEL_NAME_KEY);
        String apiToken = assertString(ctx, API_TOKEN_KEY);

        SlackConfiguration cfg = SlackConfiguration.from(ctx, apiToken);
        try (SlackClient client = new SlackClient(cfg)) {
            Response r = client.createGroupChannel(channelName);
            handleError(ctx, r, channelName);

            String channelId = extractString(r, "group", "id");
            ctx.setVariable(RESULT_KEY, channelId);
            log.info("Slack group created: {} -> {} (stored as '{}' variable)", channelName, channelId, RESULT_KEY);
        }
    }

    private static void archiveChannel(Context ctx) throws Exception {
        String channelId = assertString(ctx, CHANNEL_ID_KEY);
        String apiToken = assertString(ctx, API_TOKEN_KEY);

        SlackConfiguration cfg = SlackConfiguration.from(ctx, apiToken);
        try (SlackClient client = new SlackClient(cfg)) {
            Response r = client.archiveChannel(channelId);
            handleError(ctx, r, channelId);
        }
    }

    private static void archiveGroup(Context ctx) throws Exception {
        String channelId = assertString(ctx, CHANNEL_ID_KEY);
        String apiToken = assertString(ctx, API_TOKEN_KEY);

        SlackConfiguration cfg = SlackConfiguration.from(ctx, apiToken);
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

    @SuppressWarnings("unchecked")
    private static String extractString(Response r, String... path) {
        Map<String, Object> m = r.getParams();
        if (m == null) {
            return null;
        }

        int idx = 0;
        while (true) {
            String s = path[idx];

            Object v = m.get(s);
            if (v == null) {
                return null;
            }

            if (idx + 1 >= path.length) {
                if (v instanceof String) {
                    return (String) v;
                } else {
                    throw new IllegalStateException("Expected a string value @ " + Arrays.toString(path) + ", got: " + v);
                }
            }

            if (!(v instanceof Map)) {
                throw new IllegalStateException("Expected a JSON object, got: " + v);
            }
            m = (Map<String, Object>) v;

            idx += 1;
        }
    }

    private static Action getAction(Context ctx) {
        Object v = ctx.getVariable(ACTION_KEY);
        if (v instanceof String) {
            String s = (String) v;
            return Action.valueOf(s.trim().toUpperCase());
        }
        throw new IllegalArgumentException("'" + ACTION_KEY + "' must be a string");
    }

    public enum Action {
        CREATE,
        CREATEGROUP,
        ARCHIVE,
        ARCHIVEGROUP
    }
}
