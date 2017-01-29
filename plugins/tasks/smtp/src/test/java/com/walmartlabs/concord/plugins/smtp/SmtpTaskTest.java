package com.walmartlabs.concord.plugins.smtp;

import org.junit.Ignore;
import org.junit.Test;

public class SmtpTaskTest {

    @Test
    @Ignore
    public void test() throws Exception {
        SmtpTask t = new SmtpTask();
        t.send("host", 25, "my@mail.com", "their@mail.com", "test", "Hello!");
    }
}
