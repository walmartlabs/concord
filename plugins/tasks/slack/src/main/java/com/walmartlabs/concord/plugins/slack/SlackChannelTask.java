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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.util.Map;

import static com.walmartlabs.concord.plugins.slack.SlackClient.Response;
import static com.walmartlabs.concord.plugins.slack.SlackClient.resEntity;
import static com.walmartlabs.concord.plugins.slack.SlackConfiguration.DEFAULT_CONNECT_TIMEOUT;
import static com.walmartlabs.concord.plugins.slack.SlackConfiguration.DEFAULT_SO_TIMEOUT;
import static com.walmartlabs.concord.plugins.slack.Utils.*;


@Named("slackChannel")

public class SlackChannelTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(SlackChannelTask.class);

    private static final String CHANNEL_ID = "channelId";
    private static final String CHANNEL_NAME = "channelName";
    private static final String ACTION_KEY = "action";
    private static final String API_TOKEN = "apiToken";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void execute(Context ctx) throws Exception {
        Action action = getAction(ctx);

        switch (action) {
            case CREATE: {
                log.info("Starting 'CREATE' action");
                createChannel(ctx);
                break;
            }
            case CREATEPRIV: {
                log.info("Starting 'CREATEPRIV' action");
                createPrivChannel(ctx);
                break;
            }
            case ARCHIVE: {
                log.info("Starting 'ARCHIVE' action");
                archiveChannel(ctx, action);
                break;
            }
            case ARCHIVEPRIV: {
                log.info("Starting 'ARCHIVEPRIV' action");
                archiveChannel(ctx, action);
                break;
            }
            default:
                throw new IllegalArgumentException("Unsupported action type: " + action);
        }
    }

    private static void createChannel(Context ctx) throws Exception {
        String channelId = assertString(ctx, CHANNEL_NAME);
        String apiToken = assertString(ctx, API_TOKEN);
        String responseKey = "channel";

        try (SlackClient client = new SlackClient(buildCfg(ctx, apiToken))) {
            SlackClient.Response r = client.createChannel(channelId);
            responseHandleCreate(ctx, r, channelId, responseKey);
        } catch (Exception e) {
            throw new RuntimeException("slackChannel task error: " + e);
        }

    }

    private static void createPrivChannel(Context ctx) throws Exception {
        String channelId = assertString(ctx, CHANNEL_NAME);
        String apiToken = assertString(ctx, API_TOKEN);
        String responseKey = "group";

        try (SlackClient client = new SlackClient(buildCfg(ctx, apiToken))) {
            SlackClient.Response r = client.createPrivChannel(channelId);
            responseHandleCreate(ctx, r, channelId, responseKey);
        } catch (Exception e) {
            throw new RuntimeException("slackChannel task error: " + e);
        }

    }

    private static void archiveChannel(Context ctx, Action action) throws Exception {
        String channelId = assertString(ctx, CHANNEL_ID);
        String apiToken = assertString(ctx, API_TOKEN);
        SlackClient.Response r;

        try (SlackClient client = new SlackClient(buildCfg(ctx, apiToken))) {
            if (action.name().equals("ARCHIVE")) {
                r = client.archiveChannel(channelId);
            } else {
                r = client.archivePrivChannel(channelId);
            }
            responseHandleArchive(ctx, r, channelId);
        } catch (Exception e) {
            throw new RuntimeException("slackChannel task error: " + e);
        }

    }


    @SuppressWarnings("unchecked")
    public static SlackConfiguration buildCfg(Context ctx, String apiToken) {
        Map<String, Object> slackParams = (Map<String, Object>) ctx.getVariable("slackCfg");
        SlackConfiguration cfg = new SlackConfiguration(getToken(slackParams, "authToken", apiToken));
        cfg.setProxy(getString(slackParams, "proxyAddress"), getInteger(slackParams, "proxyPort"));
        cfg.setConnectTimeout(getInteger(slackParams, "connectTimeout", DEFAULT_CONNECT_TIMEOUT));
        cfg.setSoTimeout(getInteger(slackParams, "soTimeout", DEFAULT_SO_TIMEOUT));

        return cfg;
    }

    public static void responseHandleCreate(Context ctx, Response r, String channelId, String responseKey) throws Exception {
        Action action = getAction(ctx);
        if (!r.isOk()) {
            throw new RuntimeException("Error in action " + "'" + action + "' on channel: " + "'" + channelId + "'. " + "Error= " + "'" + r.getError() + "'");
        } else {
            String chId = objectMapper.readTree(resEntity).get(responseKey).get("id").asText();
            ctx.setVariable("slackChannelId", chId);
            log.info("Action '{}' completed on channel '{}'", action, channelId);
        }
    }


    private static void responseHandleArchive(Context ctx, Response r, String channelId) {
        Action action = getAction(ctx);
        if (!r.isOk()) {
            throw new RuntimeException("Error in action " + "'" + action + "' on channel: " + "'" + channelId + "'. " + "Error= " + "'" + r.getError() + "'");
        } else {
            log.info("Action '{}' completed on channel '{}'", action, channelId);
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

    private enum Action {
        CREATE,
        CREATEPRIV,
        ARCHIVE,
        ARCHIVEPRIV
    }
}