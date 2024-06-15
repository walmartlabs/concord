package com.walmartlabs.concord.server.security.apikey;

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

import com.walmartlabs.concord.server.cfg.EmailNotifierConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

public class EmailNotifier {

    private static final Logger log = LoggerFactory.getLogger(EmailNotifier.class);

    private final Properties mailProperties;
    private final String from;
    private final boolean enabled;

    @Inject
    public EmailNotifier(EmailNotifierConfiguration cfg) {
        this.mailProperties = buildProperties(cfg);
        this.from = cfg.getFrom();
        this.enabled = cfg.isEnabled();
    }

    private Properties buildProperties(EmailNotifierConfiguration cfg) {
        if (!cfg.isEnabled()) {
            return new Properties();
        }

        Properties result = new Properties();
        result.setProperty("mail.smtp.host", cfg.getHost());
        result.setProperty("mail.smtp.port", Integer.toString(cfg.getPort()));
        result.setProperty("mail.smtp.timeout", Long.toString(cfg.getReadTimeout().toMillis()));
        result.setProperty("mail.smtp.connectiontimeout", Long.toString(cfg.getConnectTimeout().toMillis()));
        return result;
    }

    public boolean send(String to, String subject, String text) {
        if (!enabled) {
            log.info("send ['{}', '{}'] -> email notifications are disabled", to, subject);
            return true;
        }

        try {
            Session session = Session.getInstance(mailProperties);

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject);
            message.setText(text);

            Transport.send(message);
            return true;
        } catch (Exception e) {
            log.error("send ['{}', '{}'] -> error", to, subject, e);
            return false;
        }
    }
}
