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
import com.walmartlabs.concord.sdk.InjectVariable;
import com.walmartlabs.concord.sdk.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.plugins.slack.Utils.getBoolean;

@Named("slack")
public class SlackTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(SlackTask.class);

    @Override
    @SuppressWarnings("unchecked")
    public void execute(Context ctx) {
        Action action = Action.valueOf(ContextUtils.getString(ctx, "action", Action.SENDMESSAGE.name()).toUpperCase());

        boolean ignoreErrors = getBoolean(ctx, "ignoreErrors", false);
        String channelId = ContextUtils.assertString(ctx, "channelId");

        switch (action) {
            case ADDREACTION: {
                String ts = ContextUtils.assertString(ctx, "ts");
                String reaction = ContextUtils.assertString(ctx, "reaction");
                callAddReaction(ctx, channelId, ts, ignoreErrors, reaction);
                break;
            }
            case SENDMESSAGE: {
                String ts = ContextUtils.getString(ctx, "ts");
                String text = (String) ctx.getVariable("text");
                String iconEmoji = (String) ctx.getVariable("iconEmoji");
                String username = (String) ctx.getVariable("username");
                Collection<Object> attachments = (Collection<Object>) ctx.getVariable("attachments");
                call(ctx, channelId, ts, text, iconEmoji, username, attachments, ignoreErrors);
                break;
            }
            default: {
                throw new IllegalArgumentException("Unsupported action type: " + action);
            }
        }
    }

    public void call(@InjectVariable("context") Context ctx, String channelId, String text) {
        call(ctx, channelId, null, text, null, null, null, false);
    }

    public void call(@InjectVariable("context") Context ctx,
                     String channelId, String text,
                     String iconEmoji, String username, Collection<Object> attachments) {
        call(ctx, channelId, null, text, iconEmoji, username, attachments, false);
    }

    public void call(@InjectVariable("context") Context ctx,
                     String channelId, String ts, String text,
                     String iconEmoji, String username, Collection<Object> attachments,
                     boolean ignoreErrors) {

        SlackConfiguration cfg = SlackConfiguration.from(ctx);
        try (SlackClient client = new SlackClient(cfg)) {
            SlackClient.Response r = client.message(channelId, ts, text, iconEmoji, username, attachments);
            if (!r.isOk()) {
                log.warn("Error sending a Slack message: {}", r.getError());
            } else {
                log.info("Slack message sent into '{}' channel", channelId);
            }

            ctx.setVariable("result", result(r));
        } catch (Exception e) {
            if (!ignoreErrors) {
                log.error("call ['{}', '{}', '{}', '{}', '{}', '{}'] -> error", channelId, ts, text, iconEmoji, username, attachments, e);
                throw new RuntimeException("slack task error: ", e);
            }

            log.warn("call ['{}', '{}', '{}', '{}', '{}', '{}'] -> error (ignoreErrors=true)", channelId, ts, text, iconEmoji, username, attachments, e);
            ctx.setVariable("result", errorResult(e));
        }
    }

    private void callAddReaction(@InjectVariable("context") Context ctx, String channelId, String ts, boolean ignoreErrors, String reaction) {
        SlackConfiguration cfg = SlackConfiguration.from(ctx);
        try (SlackClient client = new SlackClient(cfg)) {
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
        m.put("id", r.getChannelId());
        m.put("ts", r.getTs());
        return m;
    }

    private static Map<String, Object> errorResult(Throwable t) {
        Map<String, Object> m = new HashMap<>();
        m.put("ok", false);
        m.put("error", t.getMessage());
        return m;
    }

    private enum Action {
        SENDMESSAGE,
        ADDREACTION
    }
}
