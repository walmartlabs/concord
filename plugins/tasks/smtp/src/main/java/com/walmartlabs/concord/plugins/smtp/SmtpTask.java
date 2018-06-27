package com.walmartlabs.concord.plugins.smtp;

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


import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.InjectVariable;
import com.walmartlabs.concord.sdk.Task;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.SimpleEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.io.FileReader;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;

@Named("smtp")
public class SmtpTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(SmtpTask.class);

    @Override
    public void execute(Context ctx) throws Exception {
        Map<String, Object> smtp = getCfg(ctx, "smtpParams", "smtp");
        Map<String, Object> mail = getCfg(ctx, "mailParams", "mail");
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
        smtp.put("host", hostName);
        smtp.put("port", port);

        Map<String, Object> mail = new HashMap<>();
        mail.put("from", from);
        mail.put("to", to);
        mail.put("subject", subject);
        mail.put("message", message);
        mail.put("bcc", bcc);

        send(null, smtp, mail);
    }

    private void send(Context ctx, Map<String, Object> smtp, Map<String, Object> mail) throws Exception {
        String host = assertString(smtp, "host");
        int port = assertInt(smtp, "port");

        mail = applyTemplate(ctx, mail);

        String from = assertString(mail, "from");
        Collection<String> to = oneOrManyStrings(mail, "to");
        Collection<String> cc = zeroOrManyStrings(mail, "cc");
        Collection<String> bcc = zeroOrManyStrings(mail, "bcc");
        Collection<String> replyTo = zeroOrManyStrings(mail, "replyTo");
        String subject = (String) mail.get("subject");
        String msg = assertString(mail, "message");

        try {
            Email email = new SimpleEmail();
            email.setHostName(host);
            email.setSmtpPort(port);

            email.setFrom(from);

            for (String s : to) {
                email.addTo(s);
            }

            for (String s : cc) {
                email.addCc(s);
            }

            for (String s : bcc) {
                email.addBcc(s);
            }

            for (String s : replyTo) {
                email.addReplyTo(s);
            }

            email.setSubject(subject);
            email.setMsg(msg);

            String msgId = email.send();
            log.info("send [{}, {}] -> done, msgId: {}", smtp, mail, msgId);
        } catch (Exception e) {
            log.error("send [{}, {}] -> error", smtp, mail, e);
            throw e;
        }
    }

    private static Map<String, Object> applyTemplate(Context ctx, Map<String, Object> mailParams) throws Exception {
        String template = (String) mailParams.get("template");
        if (template == null) {
            return mailParams;
        }

        StringWriter out = new StringWriter();

        String baseDir;
        if (ctx == null) {
            baseDir = System.getProperty("user.dir");
        } else {
            baseDir = (String) ctx.getVariable("workDir");
        }

        try (FileReader in = new FileReader(baseDir + "/" + template)) {
            MustacheFactory mf = new DefaultMustacheFactory();
            Mustache mustache = mf.compile(in, template);

            Object scope = ctx != null ? ctx.toMap() : Collections.emptyMap();
            mustache.execute(out, scope);
        }

        Map<String, Object> m = new HashMap<>(mailParams);
        m.put("message", out.toString());
        return m;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getCfg(Context ctx, String a, String b) {
        Map<String, Object> m = (Map<String, Object>) ctx.getVariable(a);
        if (m == null) {
            m = (Map<String, Object>) ctx.getVariable(b);
        }
        return m;
    }

    private static String assertString(Map<String, Object> m, String k) {
        String v = m != null ? (String) m.get(k) : null;
        if (v == null) {
            throw new IllegalArgumentException("'" + k + "' is required");
        }

        return v;
    }

    private static int assertInt(Map<String, Object> m, String k) {
        Integer v = (Integer) m.get(k);
        if (v == null) {
            throw new IllegalArgumentException("'" + k + "' is required");
        }
        return v;
    }

    @SuppressWarnings("unchecked")
    private static Collection<String> zeroOrManyStrings(Map<String, Object> m, String k) {
        Object v = m.get(k);

        if (v instanceof String) {
            String s = (String) v;
            return Arrays.stream(s.split(","))
                    .map(x -> x.trim())
                    .collect(Collectors.toList());
        }

        if (v instanceof Collection) {
            Collection<?> c = (Collection<?>) v;
            c.forEach(i -> {
                if (!(i instanceof String)) {
                    throw new IllegalArgumentException("'" + k + "' - expected a list of string values, got: " + v);
                }
            });
            return (Collection<String>) c;
        }

        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private static Collection<String> oneOrManyStrings(Map<String, Object> m, String k) {
        Collection<String> c = zeroOrManyStrings(m, k);
        if (c.isEmpty()) {
            throw new IllegalArgumentException("'" + k + "' - expected a single string value or a list of strings");
        }
        return c;
    }
}
