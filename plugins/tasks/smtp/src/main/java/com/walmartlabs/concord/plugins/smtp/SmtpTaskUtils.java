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

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import org.apache.commons.mail.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.sdk.MapUtils.*;

public class SmtpTaskUtils {

    private static final Logger log = LoggerFactory.getLogger(SmtpTask.class);

    private static String assertPath(Path workDir, String path) {
        Path result = workDir.resolve(path).normalize().toAbsolutePath();
        if (!result.startsWith(workDir)) {
            throw new IllegalArgumentException("invalid attachment path: " + path);
        }

        if (!Files.exists(result)) {
            throw new IllegalArgumentException("attachment not found: " + result);
        }

        return result.toString();
    }

    private static String parseDisposition(String disposition) {
        if (disposition == null) {
            return EmailAttachment.ATTACHMENT;
        }

        if (EmailAttachment.ATTACHMENT.equals(disposition)) {
            return EmailAttachment.ATTACHMENT;
        } else if (EmailAttachment.INLINE.equals(disposition)) {
            return EmailAttachment.INLINE;
        }

        throw new IllegalArgumentException("invalid 'attachment' disposition value: '"
                + disposition + "', expected: " + EmailAttachment.ATTACHMENT
                + " or " + EmailAttachment.INLINE);
    }

    private static void processAttachments(MultiPartEmail email, List<EmailAttachment>
            attachments) throws EmailException {
        for (EmailAttachment a : attachments) {
            email.attach(a);
        }
    }

    private static boolean isHtml(Map<String, Object> mailParams) {
        String template = getString(mailParams, Constants.TEMPLATE_KEY);
        if (template == null) {
            return false;
        }
        return template.trim().endsWith(Constants.HTML_KEY);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> getTemplateParams(Map<String, Object> mailParams) {
        Object templateParam = mailParams.get(Constants.TEMPLATE_KEY);
        if (templateParam instanceof Map) {
            Map<String, Object> p = (Map<String, Object>) templateParam;
            return getMap(p, Constants.PARAMS_KEY, Collections.emptyMap());
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private static String getTemplateName(Map<String, Object> mailParams) {
        Object templateParam = mailParams.get(Constants.TEMPLATE_KEY);
        if (templateParam == null) {
            return null;
        }

        if (templateParam instanceof String) {
            return (String) templateParam;
        } else if (templateParam instanceof Map) {
            Map<String, Object> p = (Map<String, Object>) templateParam;
            return getString(p, Constants.NAME_KEY);
        }

        throw new IllegalArgumentException("invalid template param type: " + templateParam.getClass() + ". Expected String or Map");
    }

    @SuppressWarnings("unchecked")
    private static Collection<String> zeroOrManyStrings(Map<String, Object> m, String k) {
        Object v = m.get(k);

        if (v instanceof String) {
            String s = (String) v;
            return Arrays.stream(s.split(","))
                    .map(String::trim)
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

    private static Collection<String> oneOrManyStrings(Map<String, Object> m, String k) {
        Collection<String> c = zeroOrManyStrings(m, k);
        if (c.isEmpty()) {
            throw new IllegalArgumentException("'" + k + "' - expected a single string value or a list of strings");
        }
        return c;
    }

    @SuppressWarnings("unchecked")
    private static List<EmailAttachment> parseAttachments(Map<String, Object> mail, Path workDir) {
        List<Object> attachments = getList(mail, Constants.ATTACHMENTS_KEY, Collections.emptyList());
        if (attachments.isEmpty()) {
            return Collections.emptyList();
        }

        List<EmailAttachment> result = new ArrayList<>();
        for (Object o : attachments) {
            EmailAttachment a = new EmailAttachment();
            if (o == null) {
                continue;
            } else if (o instanceof String) {
                a.setPath(assertPath(workDir, (String) o));
            } else if (o instanceof Map) {
                Map<String, Object> params = (Map<String, Object>) o;
                a.setPath(assertPath(workDir, assertString(params, Constants.PATH_KEY)));
                a.setName(getString(params, Constants.NAME_KEY));
                a.setDescription(getString(params, Constants.DESCRIPTION_KEY));
                a.setDisposition(parseDisposition(getString(params, Constants.DISPOSITION_KEY)));
            } else {
                throw new IllegalArgumentException("invalid 'attachments' item type - expected a string or map, got: " + o.getClass());
            }
            result.add(a);
        }
        return result;
    }

    private static Email createEmail(Map<String, Object> mail, List<EmailAttachment> attachments) throws EmailException {
        boolean isHtml = isHtml(mail);
        String msg = assertString(mail, Constants.MESSAGE_KEY);

        if (isHtml) {
            HtmlEmail email = new HtmlEmail();
            email.setHtmlMsg(msg);
            processAttachments(email, attachments);
            return email;
        } else {
            Email email;
            if (!attachments.isEmpty()) {
                MultiPartEmail mpe = new MultiPartEmail();
                processAttachments(mpe, attachments);
                email = mpe;
            } else {
                email = new SimpleEmail();
            }
            email.setMsg(msg);
            return email;
        }
    }

    private static Map<String, Object> applyTemplate(Map<String, Object> mailParams, Path baseDir, Object scope) throws Exception {
        if (mailParams == null) {
            return null;
        }

        String template = getTemplateName(mailParams);
        if (template == null) {
            return mailParams;
        }

        StringWriter out = new StringWriter();

        try (FileReader in = new FileReader(baseDir.resolve(template).toFile())) {
            MustacheFactory mf = new DefaultMustacheFactory();
            Mustache mustache = mf.compile(in, template);

            mustache.execute(out, scope);
        }

        Map<String, Object> m = new HashMap<>(mailParams);
        m.put(Constants.MESSAGE_KEY, out.toString());
        return m;
    }

    public static void send(Map<String, Object> smtp, Map<String, Object> mail,
                            Path baseDir, Object scope, boolean debug,
                            boolean dryRunMode) throws Exception {

        if (mail == null) {
            throw new IllegalArgumentException("'mail' param is required");
        }

        String host = assertString(smtp, Constants.HOST_KEY);
        int port = assertInt(smtp, Constants.PORT_KEY);

        mail = applyTemplate(mail, baseDir, scope);

        String from = assertString(mail, Constants.FROM_KEY);
        Collection<String> to = oneOrManyStrings(mail, Constants.TO_KEY);
        Collection<String> cc = zeroOrManyStrings(mail, Constants.CC_KEY);
        Collection<String> bcc = zeroOrManyStrings(mail, Constants.BCC_KEY);
        Collection<String> replyTo = zeroOrManyStrings(mail, Constants.REPLYTO_KEY);
        String subject = getString(mail, Constants.SUBJECT_KEY);

        try {
            List<EmailAttachment> attachments = parseAttachments(mail, baseDir);
            Email email = createEmail(mail, attachments);

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

            if (dryRunMode) {
                log.info("Running in dry-run mode: Skipping sending email");
                return;
            }

            String msgId = email.send();

            if (debug) {
                log.info("send [{}, {}] -> done, msgId: {}", smtp, mail, msgId);
            } else {
                log.info("send -> done, msgId: {}", msgId);
            }
        } catch (Exception e) {
            if (debug) {
                log.error("send [{}, {}] -> error", smtp, mail, e);
            } else {
                log.error("send -> error", e);
            }
            throw e;
        }
    }

    public static Object getScope(Map<String, Object> mailParams, Map<String, Object> ctxParams) {
        Map<String, Object> templateParams = getTemplateParams(mailParams);
        Map<String, Object> result = new HashMap<>();
        result.putAll(ctxParams);
        result.putAll(templateParams);
        return result;
    }
}
