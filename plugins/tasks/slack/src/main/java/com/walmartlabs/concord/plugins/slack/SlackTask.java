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

import javax.inject.Named;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

@Named("slack")
@SuppressWarnings("unused")
public class SlackTask implements Task {

    private final SlackTaskCommon delegate = new SlackTaskCommon();

    @Override
    public void execute(Context ctx) {
        Map<String, Object> result = delegate.execute(SlackTaskParams.of(new ContextVariables(ctx), defaults(ctx)));
        ctx.setVariable("result", result);
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

        SlackConfiguration slackCfg = SlackConfiguration.from(SlackConfigurationParams.of(new ContextVariables(ctx), defaults(ctx)));
        delegate.sendMessage(slackCfg, channelId, ts, false, text, iconEmoji, username, attachments, null, ignoreErrors);
    }

    public void call(@InjectVariable("context") Context ctx,
                     String channelId, String ts, boolean replyBroadcast, String text,
                     String iconEmoji, String username, Collection<Object> attachments,
                     boolean ignoreErrors) {

        SlackConfiguration slackCfg = SlackConfiguration.from(SlackConfigurationParams.of(new ContextVariables(ctx), defaults(ctx)));
        delegate.sendMessage(slackCfg, channelId, ts, replyBroadcast, text, iconEmoji, username, attachments, null, ignoreErrors);
    }

    public void sendJsonMessage(@InjectVariable("context") Context ctx, SlackConfiguration slackCfg, String json, boolean ignoreErrors) {
        sendJsonMessage(ctx, slackCfg, json, ignoreErrors, false);
    }

    public void sendJsonMessage(@InjectVariable("context") Context ctx, SlackConfiguration slackCfg, String json, boolean ignoreErrors, boolean update) {
        Map<String, Object> result = delegate.sendJsonMessage(slackCfg, json, ignoreErrors, update);
        ctx.setVariable("result", result);
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

        Map<String, Object> result = delegate.sendMessage(slackCfg, channelId, ts, replyBroadcast, text, iconEmoji, username, attachments, null, ignoreErrors);
        ctx.setVariable("result", result);
    }

    private static Map<String, Object> defaults(Context ctx) {
        return ContextUtils.getMap(ctx, "slackCfg", Collections.emptyMap());
    }
}
