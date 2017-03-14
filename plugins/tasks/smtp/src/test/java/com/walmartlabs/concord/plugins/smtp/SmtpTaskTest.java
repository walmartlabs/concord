package com.walmartlabs.concord.plugins.smtp;

import com.icegreen.greenmail.junit.GreenMailRule;
import com.icegreen.greenmail.smtp.SmtpServer;
import com.icegreen.greenmail.util.ServerSetupTest;
import org.junit.Rule;
import org.junit.Test;

import javax.mail.internet.MimeMessage;

import static org.junit.Assert.assertEquals;

public class SmtpTaskTest {

    @Rule
    public final GreenMailRule mail = new GreenMailRule(ServerSetupTest.SMTP);

    @Test
    public void test() throws Exception {
        SmtpServer server = mail.getSmtp();

        SmtpTask t = new SmtpTask();
        t.send("localhost", server.getPort(), "my@mail.com", "their@mail.com", "test", "Hello!");

        MimeMessage[] messages = mail.getReceivedMessages();
        assertEquals(1, messages.length);
        assertEquals("Hello!\r\n", messages[0].getContent());
    }
}
