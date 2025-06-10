package com.walmartlabs.concord.plugins.slack.v2;

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

import com.walmartlabs.concord.plugins.slack.SlackConfiguration;
import com.walmartlabs.concord.plugins.slack.SlackConfigurationParams;
import com.walmartlabs.concord.plugins.slack.SlackTaskCommon;
import com.walmartlabs.concord.plugins.slack.SlackTaskParams;
import com.walmartlabs.concord.runtime.v2.sdk.*;
import com.walmartlabs.concord.sdk.InjectVariable;
import com.walmartlabs.concord.sdk.MapUtils;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Collections;
import java.util.Map;

@Named("slack")
@SuppressWarnings("unused")
public class SlackTaskV2 implements Task {

    private final SlackTaskCommon delegate = new SlackTaskCommon();
    private final Context context;

    @Inject
    public SlackTaskV2(Context context) {
        this.context = context;
    }

    @Override
    public TaskResult execute(Variables input) {
        Map<String, Object> result = delegate.execute(SlackTaskParams.of(input, context.defaultVariables().toMap()));
        return toResult(result);
    }

    public TaskResult send(String channelId, String text) {
        Map<String, Object> result = delegate.sendMessage(cfg(), channelId, null, false, text, null, null, null, null, false);
        return toResult(result);
    }

    public TaskResult sendJson(@InjectVariable("context") com.walmartlabs.concord.sdk.Context ctx, SlackConfiguration slackCfg, String json, boolean ignoreErrors) {
        Map<String, Object> result = delegate.sendJsonMessage(cfg(), json, ignoreErrors, false);
        return toResult(result);
    }

    private SlackConfiguration cfg() {
        return SlackConfiguration.from(SlackConfigurationParams.of(new MapBackedVariables(Collections.emptyMap()), context.defaultVariables().toMap()));
    }

    private static TaskResult toResult(Map<String, Object> result) {
        return TaskResult.of(MapUtils.getBoolean(result, "ok", false), MapUtils.getString(result, "error"))
                .values(result);
    }
}
