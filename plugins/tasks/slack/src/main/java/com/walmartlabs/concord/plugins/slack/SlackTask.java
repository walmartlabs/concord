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
import com.walmartlabs.concord.sdk.InjectVariable;
import com.walmartlabs.concord.sdk.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Named("slack")
public class SlackTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(SlackTask.class);

    @Override
    @SuppressWarnings("unchecked")
    public void execute(Context ctx) {
        String channelId = (String) ctx.getVariable("channelId");
        String text = (String) ctx.getVariable("text");
        String iconEmoji = (String) ctx.getVariable("iconEmoji");
        String username = (String) ctx.getVariable("username");
        Collection<Object> attachments = (Collection) ctx.getVariable("attachments");

        // Optional and should be parent thread Id (ts)
        String ts = (String) ctx.getVariable("ts");

        call(ctx, channelId, ts, text, iconEmoji, username, attachments);
    }

    public void call(@InjectVariable("context") Context ctx, String channelId, String text) {
        call(ctx, channelId, null, text, null, null, null);
    }

    public void call(@InjectVariable("context") Context ctx,
                     String channelId, String text,
                     String iconEmoji, String username, Collection<Object> attachments) {
        call(ctx, channelId, null, text, null, null, null);
    }

    public void call(@InjectVariable("context") Context ctx,
                     String channelId, String ts, String text,
                     String iconEmoji, String username, Collection<Object> attachments) {

        SlackConfiguration cfg = SlackConfiguration.from(ctx);
        try (SlackClient client = new SlackClient(cfg)) {
            SlackClient.Response r = client.message(channelId, ts, text, iconEmoji, username, attachments);
            if (!r.isOk()) {
                log.warn("Error sending a Slack message: {}", r.getError());
            } else {
                log.info("Slack message sent into '{}' channel", channelId);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("ok", r.isOk());
            result.put("error", r.getError());
            result.put("ts", r.getTs());
            ctx.setVariable("result", result);
        } catch (Exception e) {
            log.error("call ['{}', '{}', '{}', '{}', '{}', '{}'] -> error",
                    channelId, ts, text, iconEmoji, username, attachments, e);
            throw new RuntimeException("slack task error: ", e);
        }
    }
}
