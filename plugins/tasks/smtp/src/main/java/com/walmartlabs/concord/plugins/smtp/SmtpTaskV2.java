package com.walmartlabs.concord.plugins.smtp;

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

import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;

import javax.inject.Inject;
import javax.inject.Named;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

@Named("smtp")
public class SmtpTaskV2 implements Task {

    private final Context ctx;

    @Inject
    public SmtpTaskV2(Context ctx) {
        this.ctx = ctx;
    }

    @Override
    public TaskResult execute(Variables input) throws Exception {
        Map<String, Object> smtp = getCfg(ctx, input, Constants.SMTP_PARAMS_KEY, Constants.SMTP_KEY);
        Map<String, Object> mail = getCfg(ctx, input, Constants.MAIL_PARAMS_KEY, Constants.MAIL_KEY);
        Path baseDir = ctx.workingDirectory();
        Object scope = getScope(ctx, mail);
        boolean debug = input.getBoolean(Constants.DEBUG_KEY, ctx.processConfiguration().debug());

        SmtpTaskUtils.send(smtp, mail, baseDir, scope, debug, ctx.processConfiguration().dryRun());
        return TaskResult.success();
    }

    private static Object getScope(Context ctx, Map<String, Object> mailParams) {
        Map<String, Object> ctxParams = ctx != null ? ctx.variables().toMap() : Collections.emptyMap();
        return SmtpTaskUtils.getScope(mailParams, ctxParams);
    }

    private static Map<String, Object> getCfg(Context ctx, Variables input, String a, String b) {
        Map<String, Object> m = getMap(ctx, input, a);
        if (m == null) {
            m = getMap(ctx, input, b);
        }

        if (m == null) {
            return ctx.defaultVariables().toMap();
        }

        return m;
    }

    private static Map<String, Object> getMap(Context ctx, Variables input, String s) {
        Map<String, Object> m = input.getMap(s, null);
        if (m == null) {
            m = ctx.variables().getMap(s, null);
        }
        return m;
    }
}
