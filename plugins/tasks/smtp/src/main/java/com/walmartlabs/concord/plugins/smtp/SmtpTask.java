package com.walmartlabs.concord.plugins.smtp;

import com.walmartlabs.concord.common.Task;
import io.takari.bpm.api.BpmnError;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.SimpleEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;

@Named
public class SmtpTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(SmtpTask.class);

    @Override
    public String getKey() {
        return "smtp";
    }

    public void send(String hostName, int port, String from, String to, String subject, String message) {
        try {
            Email email = new SimpleEmail();
            email.setHostName(hostName);
            email.setSmtpPort(port);
            email.setFrom(from);
            email.addTo(to);
            email.setSubject(subject);
            email.setMsg(message);
            String msgId = email.send();
            log.info("send ['{}', {}, '{}', '{}', '{}', '{}] -> done, msgId: {}", hostName, port, from, to, subject, message, msgId);
        } catch (Exception e) {
            log.error("send ['{}', {}, '{}', '{}', '{}', '{}] -> error", hostName, port, from, to, subject, message, e);
            throw new BpmnError("smtpError", e);
        }
    }
}
