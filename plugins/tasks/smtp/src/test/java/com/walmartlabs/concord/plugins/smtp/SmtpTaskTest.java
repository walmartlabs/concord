package com.walmartlabs.concord.plugins.smtp;

import static org.junit.Assert.assertEquals;

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
}
