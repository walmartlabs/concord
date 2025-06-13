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

import com.walmartlabs.concord.runtime.v2.sdk.Variables;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public class SlackTaskParams {

    public static SlackTaskParams of(Variables input, Map<String, Object> defaults) {
        Variables variables = Utils.merge(input, defaults);

        SlackTaskParams p = new SlackTaskParams(variables);
        switch (p.action()) {
            case SENDMESSAGE:
            case UPDATEMESSAGE: {
                return new SendMessageParams(variables);
            }
            case ADDREACTION: {
                return new AddReactionParams(variables);
            }
            default: {
                throw new IllegalArgumentException("Unsupported action type: " + p.action());
            }
        }
    }

    private static final String ACTION = "action";
    private static final String IGNORE_ERRORS = "ignoreErrors";

    protected final Variables variables;
    private final SlackConfigurationParams cfg;

    public SlackTaskParams(Variables variables) {
        this.variables = variables;
        this.cfg = new SlackConfigurationParams(variables);
    }

    public Action action() {
        String action = variables.getString(ACTION, Action.SENDMESSAGE.name());
        try {
            return Action.valueOf(action.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Unknown action: '" + action + "'. Available actions: " + Arrays.toString(Action.values()));
        }
    }

    public SlackConfigurationParams cfg() {
        return cfg;
    }

    public static class AddReactionParams extends SlackTaskParams {

        private static final String REACTION = "reaction";
        private static final String CHANNEL_ID = "channelId";
        private static final String TS = "ts";

        public AddReactionParams(Variables variables) {
            super(variables);
        }

        public String reaction() {
            return variables.assertString(REACTION);
        }

        public String channelId() {
            return variables.assertString(CHANNEL_ID);
        }

        public String ts() {
            return variables.assertString(TS);
        }
    }

    public static class SendMessageParams extends SlackTaskParams {

        private static final String ATTACHMENTS = "attachments";
        private static final String JSON = "json";
        private static final String ICON_EMOJI = "iconEmoji";
        private static final String REPLY_BROADCAST = "replyBroadcast";
        private static final String USERNAME = "username";
        private static final String TEXT = "text";
        private static final String BLOCKS = "blocks";
        private static final String CHANNEL_ID = "channelId";
        private static final String TS = "ts";

        public SendMessageParams(Variables variables) {
            super(variables);
        }

        public String json() {
            return variables.getString(JSON);
        }

        public boolean replyBroadcast() {
            return variables.getBoolean(REPLY_BROADCAST, false);
        }

        public String iconEmoji() {
            return variables.getString(ICON_EMOJI);
        }

        public String username() {
            return variables.getString(USERNAME);
        }

        public Collection<Object> attachments() {
            return variables.getCollection(ATTACHMENTS, Collections.emptyList());
        }

        public String text() {
            return variables.getString(TEXT);
        }

        public Collection<Object> blocks() {
            return variables.getCollection(BLOCKS, Collections.emptyList());
        }

        public String channelId() {
            return variables.assertString(CHANNEL_ID);
        }

        public String ts() {
            return variables.getString(TS);
        }
    }

    public boolean ignoreErrors() {
        return variables.getBoolean(IGNORE_ERRORS, false);
    }

    public enum Action {
        SENDMESSAGE,
        ADDREACTION,
        UPDATEMESSAGE
    }
}
