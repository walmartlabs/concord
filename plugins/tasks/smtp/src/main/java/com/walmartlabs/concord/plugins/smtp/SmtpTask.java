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

import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.ContextUtils;
import com.walmartlabs.concord.sdk.InjectVariable;
import com.walmartlabs.concord.sdk.Task;

import javax.inject.Named;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Named("smtp")
public class SmtpTask implements Task {

    @Override
    public void execute(Context ctx) throws Exception {
        Map<String, Object> smtp = getCfg(ctx, Constants.SMTP_PARAMS_KEY, Constants.SMTP_KEY);
        Map<String, Object> mail = getCfg(ctx, Constants.MAIL_PARAMS_KEY, Constants.MAIL_KEY);
        send(ctx, smtp, mail);
    }

    @Deprecated
    public void call(@InjectVariable("context") Context ctx, Map<String, Object> smtpParams, Map<String, Object> mailParams) throws Exception {
        send(ctx, smtpParams, mailParams);
    }

    @Deprecated
    public void call(Map<String, Object> smtpParams, Map<String, Object> mailParams) throws Exception {
        send(null, smtpParams, mailParams);
    }

    @Deprecated
    public void send(String hostName, int port, String from, String to, String subject, String message, String bcc) throws Exception {
        Map<String, Object> smtp = new HashMap<>();
        smtp.put(Constants.HOST_KEY, hostName);
        smtp.put(Constants.PORT_KEY, port);

        Map<String, Object> mail = new HashMap<>();
        mail.put(Constants.FROM_KEY, from);
        mail.put(Constants.TO_KEY, to);
        mail.put(Constants.SUBJECT_KEY, subject);
        mail.put(Constants.MESSAGE_KEY, message);
        mail.put(Constants.BCC_KEY, bcc);

        send(null, smtp, mail);
    }

    private static void send(Context ctx, Map<String, Object> smtp, Map<String, Object> mail) throws Exception {
        Path baseDir = getWorkDir(ctx);
        Object scope = getScope(ctx, mail);
        boolean debug = ContextUtils.getBoolean(ctx, Constants.DEBUG_KEY, false);

        SmtpTaskUtils.send(smtp, mail, baseDir, scope, debug, false);
    }

    private static Path getWorkDir(Context ctx) {
        if (ctx != null) {
            return ContextUtils.getWorkDir(ctx);
        }
        return Paths.get(System.getProperty("user.dir"));
    }

    private static Object getScope(Context ctx, Map<String, Object> mailParams) {
        Map<String, Object> ctxParams = ctx != null ? ctx.toMap() : Collections.emptyMap();
        return SmtpTaskUtils.getScope(mailParams, ctxParams);
    }

    private static Map<String, Object> getCfg(Context ctx, String a, String b) {
        Map<String, Object> m = ContextUtils.getMap(ctx, a);
        if (m == null) {
            m = ContextUtils.getMap(ctx, b);
        }
        return m;
    }
}
