package com.walmartlabs.concord.plugins.slack;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import java.util.Map;

public class SlackChannelTaskParams {

    public static SlackChannelTaskParams of(Variables input, Map<String, Object> defaults) {
        Variables variables = Utils.merge(input, defaults);

        SlackChannelTaskParams p = new SlackChannelTaskParams(variables);
        switch (p.action()) {
            case CREATE: {
                return new CreateChannelParams(variables);
            }
            case CREATEGROUP: {
                return new CreateGroupParams(variables);
            }
            case ARCHIVE: {
                return new ArchiveChannelParams(variables);
            }
            case ARCHIVEGROUP: {
                return new ArchiveGroupParams(variables);
            }
            default: {
                throw new IllegalArgumentException("Unsupported action type: " + p.action());
            }
        }
    }

    private static final String ACTION = "action";

    protected final Variables variables;
    private final SlackConfigurationParams cfg;

    public SlackChannelTaskParams(Variables variables) {
        this.variables = variables;
        this.cfg = new SlackConfigurationParams(variables);
    }

    public Action action() {
        String action = variables.assertString(ACTION);
        try {
            return Action.valueOf(action.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Unknown action: '" + action + "'. Available actions: " + Arrays.toString(SlackTaskParams.Action.values()));
        }
    }

    public static class CreateChannelParams extends SlackChannelTaskParams {

        private static final String CHANNEL_NAME = "channelName";

        public CreateChannelParams(Variables variables) {
            super(variables);
        }

        public String channelName() {
            return variables.assertString(CHANNEL_NAME);
        }
    }

    public static class CreateGroupParams extends SlackChannelTaskParams {

        private static final String CHANNEL_NAME = "channelName";

        public CreateGroupParams(Variables variables) {
            super(variables);
        }

        public String channelName() {
            return variables.assertString(CHANNEL_NAME);
        }
    }

    public static class ArchiveChannelParams extends SlackChannelTaskParams {

        private static final String CHANNEL_ID = "channelId";

        public ArchiveChannelParams(Variables variables) {
            super(variables);
        }

        public String channelId() {
            return variables.assertString(CHANNEL_ID);
        }
    }

    public static class ArchiveGroupParams extends SlackChannelTaskParams {

        private static final String CHANNEL_ID = "channelId";

        public ArchiveGroupParams(Variables variables) {
            super(variables);
        }

        public String channelId() {
            return variables.assertString(CHANNEL_ID);
        }
    }

    public SlackConfigurationParams cfg() {
        return cfg;
    }

    public enum Action {
        CREATE,
        CREATEGROUP,
        ARCHIVE,
        ARCHIVEGROUP
    }
}
