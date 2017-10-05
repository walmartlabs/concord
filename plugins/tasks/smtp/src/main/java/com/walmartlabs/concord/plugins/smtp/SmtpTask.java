package com.walmartlabs.concord.plugins.smtp;

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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Named("smtp")
public class SmtpTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(SmtpTask.class);
    private static final String TEMPLATE_APPLIED_MARKER = "__templateApplied";

    @Override
    @SuppressWarnings("unchecked")
    public void execute(Context ctx) throws Exception {
        Map<String, Object> smtpParams = (Map<String, Object>) ctx.getVariable("smtpParams");
        if (smtpParams == null) {
            smtpParams = (Map<String, Object>) ctx.getVariable("smtp");
        }

        Map<String, Object> mailParams = (Map<String, Object>) ctx.getVariable("mailParams");
        if (mailParams == null) {
            mailParams = (Map<String, Object>) ctx.getVariable("mail");
        }

        call(smtpParams, mailParams);
    }

    public void call(@InjectVariable("context") Context ctx, Map<String, Object> smtpParams, Map<String, Object> mailParams) throws Exception {
        call(smtpParams, applyTemplate(ctx, mailParams));
    }

    public void call(Map<String, Object> smtpParams, Map<String, Object> mailParams) throws Exception {
        mailParams = applyTemplate(null, mailParams);

        send((String) smtpParams.get("host"), (int) smtpParams.get("port"),
                (String) mailParams.get("from"), (String) mailParams.get("to"),
                (String) mailParams.get("subject"), (String) mailParams.get("message"), (String) mailParams.get("bcc"));
    }

    public void send(String hostName, int port, String from, String to, String subject, String message, String bcc) throws Exception {
        try {
            Email email = new SimpleEmail();
            email.setHostName(hostName);
            email.setSmtpPort(port);
            email.setFrom(from);
            email.addTo(to);
            email.setSubject(subject);
            email.setMsg(message);
            if( bcc != null ){
            	email.addBcc(bcc);
            }
            String msgId = email.send();
            log.info("send ['{}', {}, '{}', '{}', '{}', '{}'] -> done, msgId: {}", hostName, port, from, to, subject, message, msgId);
        } catch (Exception e) {
            log.error("send ['{}', {}, '{}', '{}', '{}', '{}'] -> error", hostName, port, from, to, subject, message, e);
            throw e;
        }
    }

    private static Map<String, Object> applyTemplate(Context ctx, Map<String, Object> mailParams) throws Exception {
        if (mailParams.containsKey(TEMPLATE_APPLIED_MARKER)) {
            return mailParams;
        }

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
        m.put(TEMPLATE_APPLIED_MARKER, true);
        return m;
    }
}
