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

import com.walmartlabs.concord.common.TimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.plugins.slack.SlackTaskParams.*;

public class SlackTaskCommon {

    private static final Logger log = LoggerFactory.getLogger(SlackTask.class);

    public Map<String, Object> execute(SlackTaskParams in) {
        Action action = in.action();

        switch (action) {
            case ADDREACTION: {
                return addReaction((AddReactionParams)in);
            }
            case SENDMESSAGE: {
                return sendMessage((SendMessageParams)in);
            }
            case UPDATEMESSAGE: {
                return sendMessage((SendMessageParams)in, true);
            }
            default: {
                throw new IllegalArgumentException("Unsupported action type: " + action);
            }
        }
    }

    private Map<String, Object> sendMessage(SendMessageParams in) {
        return sendMessage(in, false);
    }

    private Map<String, Object> sendMessage(SendMessageParams in, boolean update) {
        SlackConfiguration slackCfg = SlackConfiguration.from(in.cfg());

        String json = in.json();
        if (json != null) {
            return sendJsonMessage(slackCfg, json, in.ignoreErrors(), update);
        } else {
            return sendMessage(slackCfg, in.channelId(), in.ts(), in.replyBroadcast(), in.text(), in.iconEmoji(), in.username(), in.attachments(), in.ignoreErrors());
        }
    }

    public Map<String, Object> sendJsonMessage(SlackConfiguration slackCfg, TimeProvider timeProvider, String json, boolean ignoreErrors, boolean update) {
        try (SlackClient client = new SlackClient(slackCfg, timeProvider)) {
            SlackClient.Response r;
            if (update) {
                r = client.updateJsonMessage(json);
            } else {
                r = client.postJsonMessage(json);
            }
            if (!r.isOk()) {
                log.warn("Error sending a Slack message: {}", r.getError());
            }
            return result(r);
        } catch (Exception e) {
            if (!ignoreErrors) {
                log.error("call ['{}', '{}'] -> error", json, e);
                throw new RuntimeException("slack task error: ", e);
            }
            log.warn("call ['{}', '{}'] -> error (ignoreErrors=true)", json, e);
            return errorResult(e);
        }
    }

    public Map<String, Object> sendMessage(SlackConfiguration slackCfg,
                            TimeProvider timeProvider,
                            String channelId,
                            String ts,
                            boolean replyBroadcast,
                            String text,
                            String iconEmoji,
                            String username,
                            Collection<Object> attachments,
                            boolean ignoreErrors) {

        try {
            SlackClient.Response r = Slack.sendMessage(slackCfg, timeProvider, channelId, ts, replyBroadcast, text, iconEmoji, username, attachments);
            return result(r);
        } catch (Exception e) {
            if (!ignoreErrors) {
                log.error("call ['{}', '{}', '{}', '{}', '{}', '{}', '{}'] -> error", channelId, ts, replyBroadcast, text, iconEmoji, username, attachments, e);
                throw new RuntimeException("slack task error: ", e);
            }

            log.warn("call ['{}', '{}', '{}', '{}', '{}', '{}', '{}'] -> error (ignoreErrors=true)", channelId, ts, replyBroadcast, text, iconEmoji, username, attachments, e);
            return errorResult(e);
        }
    }

    private Map<String, Object> addReaction(AddReactionParams in) {
        SlackConfiguration slackCfg = SlackConfiguration.from(in.cfg());

        String channelId = in.channelId();
        String ts = in.ts();
        String reaction = in.reaction();

        try (SlackClient client = new SlackClient(slackCfg)) {
            SlackClient.Response r = client.addReaction(channelId, ts, reaction);

            if (!r.isOk()) {
                log.warn("Error adding reaction to Slack message: {}", r.getError());
            } else {
                log.info("Reaction '{}' added to the Slack message '{}'", reaction, ts);
            }

            return result(r);
        } catch (Exception e) {
            if (!in.ignoreErrors()) {
                log.error("callAddReaction ['{}', '{}', '{}'] -> error", channelId, ts, reaction, e);
                throw new RuntimeException("slack task error: ", e);
            }

            log.warn("callAddReaction ['{}', '{}', '{}', '{}'] -> error (ignoreErrors=true)", channelId, ts, reaction, e);
            return errorResult(e);
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
}
