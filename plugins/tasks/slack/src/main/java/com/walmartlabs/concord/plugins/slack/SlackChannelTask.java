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
import com.walmartlabs.concord.sdk.Task;

import javax.inject.Named;
import java.util.Collections;
import java.util.Map;

@Named("slackChannel")
@SuppressWarnings("unused")
public class SlackChannelTask implements Task {

    private final SlackChannelTaskCommon delegate = new SlackChannelTaskCommon();

    @Override
    public void execute(Context ctx) throws Exception {
        Map<String, Object> result = delegate.execute(SlackChannelTaskParams.of(new ContextVariables(ctx), defaults(ctx)));
        result.forEach(ctx::setVariable);
    }

    private static Map<String, Object> defaults(Context ctx) {
        return ContextUtils.getMap(ctx, "slackCfg", Collections.emptyMap());
    }

}
