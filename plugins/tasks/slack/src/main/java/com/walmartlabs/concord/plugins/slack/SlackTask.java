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

import com.walmartlabs.concord.sdk.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Named("slack")
public class SlackTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(SlackTask.class);

    @Override
    public void execute(Context ctx) {
        Action action = getAction(ctx);

        Map<String, Object> args = Utils.collectAgs(ctx);

        switch (action) {
            case ADDREACTION: {
                addReaction(ctx, args);
                break;
            }
            case SENDMESSAGE: {
                sendMessage(ctx, args);
                break;
            }
            case UPDATEMESSAGE: {
                sendMessage(ctx, args, true);
                break;
            }
            default: {
                throw new IllegalArgumentException("Unsupported action type: " + action);
            }
        }
    }

    public void call(@InjectVariable("context") Context ctx, String channelId, String text) {
        call(ctx, channelId, null, false, text, null, null, null, false);
    }

    public void call(@InjectVariable("context") Context ctx,
                     String channelId, String text,
                     String iconEmoji, String username, Collection<Object> attachments) {

        call(ctx, channelId, null, false, text, iconEmoji, username, attachments, false);
    }

    public void call(@InjectVariable("context") Context ctx,
                     String channelId, String ts, String text,
                     String iconEmoji, String username, Collection<Object> attachments,
                     boolean ignoreErrors) {

        Map<String, Object> args = Utils.collectAgs(ctx);
        SlackConfiguration slackCfg = SlackConfiguration.from(args);

        sendMessage(ctx, slackCfg, channelId, ts, false, text, iconEmoji, username, attachments, ignoreErrors);
    }

    public void call(@InjectVariable("context") Context ctx,
                     String channelId, String ts, boolean replyBroadcast, String text,
                     String iconEmoji, String username, Collection<Object> attachments,
                     boolean ignoreErrors) {

        Map<String, Object> args = Utils.collectAgs(ctx);
        SlackConfiguration slackCfg = SlackConfiguration.from(args);

        sendMessage(ctx, slackCfg, channelId, ts, replyBroadcast, text, iconEmoji, username, attachments, ignoreErrors);
    }

    private void sendMessage(Context ctx, Map<String, Object> args) {
        sendMessage(ctx, args, false);
    }

    private void sendMessage(Context ctx, Map<String, Object> args, boolean update) {
        SlackConfiguration slackCfg = SlackConfiguration.from(args);

        boolean ignoreErrors = MapUtils.getBoolean(args, "ignoreErrors", false);
        String json = MapUtils.getString(args, "json");
        if (json != null) {
            sendJsonMessage(ctx, slackCfg, json, ignoreErrors, update);
        } else {
            boolean replyBroadcast = MapUtils.getBoolean(args, "replyBroadcast", false);
            String channelId = MapUtils.assertString(args, "channelId");
            String ts = MapUtils.getString(args, "ts");
            String text = MapUtils.getString(args, "text");
            String iconEmoji = MapUtils.getString(args, "iconEmoji");
            String username = MapUtils.getString(args, "username");
            Collection<Object> attachments = MapUtils.getList(args, "attachments", Collections.emptyList());

            sendMessage(ctx, slackCfg, channelId, ts, replyBroadcast, text, iconEmoji, username, attachments, ignoreErrors);
        }
    }

    public void sendJsonMessage(@InjectVariable("context") Context ctx, SlackConfiguration slackCfg, String json, boolean ignoreErrors) {
        sendJsonMessage(ctx, slackCfg, json, ignoreErrors, false);
    }

    public void sendJsonMessage(@InjectVariable("context") Context ctx, SlackConfiguration slackCfg, String json, boolean ignoreErrors, boolean update) {
        try (SlackClient client = new SlackClient(slackCfg)) {
            SlackClient.Response r;
            if (update) {
                r = client.updateJsonMessage(json);
            } else {
                r = client.postJsonMessage(json);
            }
            if (!r.isOk()) {
                log.warn("Error sending a Slack message: {}", r.getError());
            }
            ctx.setVariable("result", result(r));
        } catch (Exception e) {
            if (!ignoreErrors) {
                log.error("call ['{}', '{}'] -> error", json, e);
                throw new RuntimeException("slack task error: ", e);
            }
            log.warn("call ['{}', '{}'] -> error (ignoreErrors=true)", json, e);
            ctx.setVariable("result", errorResult(e));
        }
    }

    public void sendMessage(@InjectVariable("context") Context ctx,
                            SlackConfiguration slackCfg,
                            String channelId,
                            String ts,
                            boolean replyBroadcast,
                            String text,
                            String iconEmoji,
                            String username,
                            Collection<Object> attachments,
                            boolean ignoreErrors) {

        try (SlackClient client = new SlackClient(slackCfg)) {
            SlackClient.Response r = client.message(channelId, ts, replyBroadcast, text, iconEmoji, username, attachments);
            if (!r.isOk()) {
                log.warn("Error sending a Slack message: {}", r.getError());
            } else {
                log.debug("Slack message sent into '{}' channel", channelId);
            }

            ctx.setVariable("result", result(r));
        } catch (Exception e) {
            if (!ignoreErrors) {
                log.error("call ['{}', '{}', '{}', '{}', '{}', '{}', '{}'] -> error", channelId, ts, replyBroadcast, text, iconEmoji, username, attachments, e);
                throw new RuntimeException("slack task error: ", e);
            }

            log.warn("call ['{}', '{}', '{}', '{}', '{}', '{}', '{}'] -> error (ignoreErrors=true)", channelId, ts, replyBroadcast, text, iconEmoji, username, attachments, e);
            ctx.setVariable("result", errorResult(e));
        }
    }

    private void addReaction(Context ctx, Map<String, Object> args) {
        SlackConfiguration slackCfg = SlackConfiguration.from(args);

        boolean ignoreErrors = MapUtils.getBoolean(args, "ignoreErrors", false);
        String channelId = MapUtils.assertString(args, "channelId");
        String ts = MapUtils.assertString(args, "ts");
        String reaction = MapUtils.assertString(args, "reaction");

        try (SlackClient client = new SlackClient(slackCfg)) {
            SlackClient.Response r = client.addReaction(channelId, ts, reaction);

            if (!r.isOk()) {
                log.warn("Error adding reaction to Slack message: {}", r.getError());
            } else {
                log.info("Reaction '{}' added to the Slack message '{}'", reaction, ts);
            }

            ctx.setVariable("result", result(r));
        } catch (Exception e) {
            if (!ignoreErrors) {
                log.error("callAddReaction ['{}', '{}', '{}'] -> error", channelId, ts, reaction, e);
                throw new RuntimeException("slack task error: ", e);
            }

            log.warn("callAddReaction ['{}', '{}', '{}', '{}'] -> error (ignoreErrors=true)", channelId, ts, reaction, e);
            ctx.setVariable("result", errorResult(e));
        }
    }

    private static Map<String, Object> result(SlackClient.Response r) {
        Map<String, Object> m = new HashMap<>();
        m.put("ok", r.isOk());
        m.put("error", r.getError());
        m.put("id", Utils.extractString(r, "channel"));
        m.put("ts", r.getTs());
        return m;
    }

    private static Map<String, Object> errorResult(Throwable t) {
        Map<String, Object> m = new HashMap<>();
        m.put("ok", false);
        m.put("error", t.getMessage());
        return m;
    }

    private static Action getAction(Context ctx) {
        String s = ContextUtils.getString(ctx, TaskParams.ACTION.getKey(), Action.SENDMESSAGE.name());
        return Action.valueOf(s.toUpperCase());
    }

    private enum Action {
        SENDMESSAGE,
        ADDREACTION,
        UPDATEMESSAGE
    }
}
