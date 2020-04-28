package com.walmartlabs.concord.plugins.smtp;

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

import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskContext;
import com.walmartlabs.concord.runtime.v2.sdk.WorkingDirectory;
import com.walmartlabs.concord.sdk.MapUtils;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;

@Named("smtp")
public class SmtpTaskV2 implements Task {

    private final WorkingDirectory workDir;

    @Inject
    public SmtpTaskV2(WorkingDirectory workDir) {
        this.workDir = workDir;
    }

    @Override
    public Serializable execute(TaskContext ctx) throws Exception {
        Map<String, Object> smtp = getCfg(ctx, Constants.SMTP_PARAMS_KEY, Constants.SMTP_KEY);
        Map<String, Object> mail = getCfg(ctx, Constants.MAIL_PARAMS_KEY, Constants.MAIL_KEY);
        Path baseDir = getWorkDir();
        Object scope = getScope(ctx, mail);
        boolean debug = MapUtils.getBoolean(ctx.input(), Constants.DEBUG_KEY, false);

        SmtpTaskUtils.send(smtp, mail, baseDir, scope, debug);
        return null;
    }

    private Path getWorkDir() {
        if (workDir.getValue() != null) {
            return workDir.getValue();
        }
        return Paths.get(System.getProperty("user.dir"));
    }

    private static Object getScope(TaskContext ctx, Map<String, Object> mailParams) {
        Map<String, Object> ctxParams = ctx != null ? ctx.globalVariables().toMap() : Collections.emptyMap();
        return SmtpTaskUtils.getScope(mailParams, ctxParams);
    }

    private Map<String, Object> getCfg(TaskContext ctx, String a, String b) {
        Map<String, Object> m = getMap(ctx, a);
        if (m == null) {
            m = getMap(ctx, b);
        }

        return m;
    }

    private Map<String, Object> getMap(TaskContext ctx, String s) {
        Map<String, Object> m = MapUtils.getMap(ctx.input(), s, null);
        if (m == null) {
            m = MapUtils.getMap(ctx.globalVariables().toMap(), s, null);
        }
        return m;
    }
}
