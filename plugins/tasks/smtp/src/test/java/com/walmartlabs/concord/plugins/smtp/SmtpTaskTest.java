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


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.mail.internet.MimeMessage;

import org.junit.Rule;
import org.junit.Test;

import com.icegreen.greenmail.junit.GreenMailRule;
import com.icegreen.greenmail.smtp.SmtpServer;
import com.icegreen.greenmail.util.ServerSetupTest;

public class SmtpTaskTest {

    @Rule
    public final GreenMailRule mail = new GreenMailRule(ServerSetupTest.SMTP);

    @Test
    public void test() throws Exception {
        SmtpServer server = mail.getSmtp();

        SmtpTask t = new SmtpTask();
        t.send("localhost", server.getPort(), "my@mail.com", "their@mail.com", "test", "Hello!", "another@mail.com");

        MimeMessage[] messages = mail.getReceivedMessages();
        assertEquals(2, messages.length);
        assertEquals("Hello!\r\n", messages[0].getContent());
        
        mail.reset();
    }
    
    @Test
    public void testNoBcc() throws Exception {
        SmtpServer server = mail.getSmtp();

        SmtpTask t = new SmtpTask();
        t.send("localhost", server.getPort(), "my@mail.com", "their@mail.com", "test", "Hello!", null);

        MimeMessage[] messages = mail.getReceivedMessages();
        assertEquals(1, messages.length);
        assertEquals("Hello!\r\n", messages[0].getContent());
        assertEquals(1, messages[0].getAllRecipients().length);
        
        mail.reset();
    }
    
    @Test
    public void testNoBccViaCall() throws Exception {
        SmtpServer server = mail.getSmtp();

        Map<String, Object> smtpParams = new HashMap<String, Object>();
        Map<String, Object> mailParams = new HashMap<String, Object>();
        
        smtpParams.put("host", "localhost");
        smtpParams.put("port", server.getPort());
        
        mailParams.put("from", "my@mail.com");
        mailParams.put("to", "their@mail.com");
        mailParams.put("subject", "test");
        mailParams.put("message", "Hello!");
        
        SmtpTask t = new SmtpTask();
        t.call(smtpParams, mailParams);

        MimeMessage[] messages = mail.getReceivedMessages();
        assertEquals(1, messages.length);
        assertEquals("Hello!\r\n", messages[0].getContent());
        assertEquals(1, messages[0].getAllRecipients().length);
        
        mail.reset();
    }

    @Test
    public void testMultipleTo() throws Exception {
        SmtpServer server = mail.getSmtp();

        Map<String, Object> smtpParams = new HashMap<>();
        smtpParams.put("host", "localhost");
        smtpParams.put("port", server.getPort());

        Map<String, Object> mailParams = new HashMap<>();
        mailParams.put("from", "my@mail.com");
        mailParams.put("to", Arrays.asList("aaa@mail.com", "bbb@mail.com"));
        mailParams.put("subject", "test");
        mailParams.put("message", "Hello!");

        SmtpTask t = new SmtpTask();
        t.call(smtpParams, mailParams);

        MimeMessage[] messages = mail.getReceivedMessages();
        assertEquals(2, messages.length);
        assertEquals("Hello!\r\n", messages[0].getContent());

        mail.reset();
    }

    @Test
    public void testMultipleToComma() throws Exception {
        SmtpServer server = mail.getSmtp();

        Map<String, Object> smtpParams = new HashMap<>();
        smtpParams.put("host", "localhost");
        smtpParams.put("port", server.getPort());

        Map<String, Object> mailParams = new HashMap<>();
        mailParams.put("from", "my@mail.com");
        mailParams.put("to", "aaa@mail.com, bbb@mail.com");
        mailParams.put("subject", "test");
        mailParams.put("message", "Hello!");

        SmtpTask t = new SmtpTask();
        t.call(smtpParams, mailParams);

        MimeMessage[] messages = mail.getReceivedMessages();
        assertEquals(2, messages.length);
        assertEquals("Hello!\r\n", messages[0].getContent());

        mail.reset();
    }

    @Test
    public void testNoSubject() throws Exception {
        SmtpServer server = mail.getSmtp();

        Map<String, Object> smtpParams = new HashMap<>();
        smtpParams.put("host", "localhost");
        smtpParams.put("port", server.getPort());

        Map<String, Object> mailParams = new HashMap<>();
        mailParams.put("from", "my@mail.com");
        mailParams.put("to", "aaa@mail.com");
        mailParams.put("message", "Hello!");

        SmtpTask t = new SmtpTask();
        t.call(smtpParams, mailParams);

        MimeMessage[] messages = mail.getReceivedMessages();
        assertEquals(1, messages.length);
        assertEquals("Hello!\r\n", messages[0].getContent());

        mail.reset();
    }

    @Test
    public void testNoMessage() throws Exception {
        SmtpServer server = mail.getSmtp();

        Map<String, Object> smtpParams = new HashMap<>();
        smtpParams.put("host", "localhost");
        smtpParams.put("port", server.getPort());

        Map<String, Object> mailParams = new HashMap<>();
        mailParams.put("from", "my@mail.com");
        mailParams.put("to", "aaa@mail.com");
        mailParams.put("subject", "test");

        try {
            SmtpTask t = new SmtpTask();
            t.call(smtpParams, mailParams);
            fail("should fail");
        } catch (IllegalArgumentException e) {
        }

        mail.reset();
    }
}
