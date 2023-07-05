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

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.MapBackedVariables;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.mail.internet.MimeMessage;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SmtpTaskV2Test {

    @RegisterExtension
    GreenMailExtension mailServer = new GreenMailExtension(ServerSetupTest.SMTP);

    @AfterEach
    public void cleanup() {
        mailServer.reset();
    }

    @Test
    public void testWithPolicyDefaults() throws Exception {
        Map<String, Object> smtpParams = new HashMap<>();
        smtpParams.put("host", "localhost");
        smtpParams.put("port", mailServer.getSmtp().getPort());

        Map<String, Object> mail = new HashMap<>();
        mail.put("from", "me@localhost");
        mail.put("to", "you@localhost");
        mail.put("message", "Default vars from policy.");

        Context ctx = mock(Context.class);
        when(ctx.processConfiguration()).thenReturn(ProcessConfiguration.builder().build());
        when(ctx.workingDirectory()).thenReturn(Paths.get(System.getProperty("user.dir")));
        when(ctx.variables()).thenReturn(new MapBackedVariables(Collections.emptyMap()));
        when(ctx.defaultVariables()).thenReturn(new MapBackedVariables(smtpParams));

        SmtpTaskV2 t = new SmtpTaskV2(ctx);
        t.execute(new MapBackedVariables(Collections.singletonMap("mail", mail)));

        MimeMessage[] messages = mailServer.getReceivedMessages();
        assertEquals(1, messages.length);
        assertEquals("Default vars from policy.\r\n", messages[0].getContent());
    }

    @Test
    public void testWithProcessDefaults() throws Exception {
        Map<String, Object> smtpParams = new HashMap<>();
        smtpParams.put("host", "localhost");
        smtpParams.put("port", mailServer.getSmtp().getPort());

        Map<String, Object> mail = new HashMap<>();
        mail.put("from", "me@localhost");
        mail.put("to", "you@localhost");
        mail.put("message", "Default vars from process arguments.");

        Context ctx = mock(Context.class);
        when(ctx.processConfiguration()).thenReturn(ProcessConfiguration.builder().build());
        when(ctx.workingDirectory()).thenReturn(Paths.get(System.getProperty("user.dir")));
        when(ctx.variables()).thenReturn(new MapBackedVariables(Collections.singletonMap("smtpParams", smtpParams)));
        when(ctx.defaultVariables()).thenReturn(new MapBackedVariables(Collections.emptyMap()));

        SmtpTaskV2 t = new SmtpTaskV2(ctx);
        t.execute(new MapBackedVariables(Collections.singletonMap("mail", mail)));

        MimeMessage[] messages = mailServer.getReceivedMessages();
        assertEquals(1, messages.length);
        assertEquals("Default vars from process arguments.\r\n", messages[0].getContent());
    }

    @Test
    public void testWithBothDefaults() throws Exception {
        Map<String, Object> policyDefaults = new HashMap<>();
        policyDefaults.put("host", "badserver");
        policyDefaults.put("port", -1);

        // Process arg defaults override policy defaults
        Map<String, Object> processArgsDefaults = new HashMap<>();
        processArgsDefaults.put("host", "localhost");
        processArgsDefaults.put("port", mailServer.getSmtp().getPort());

        Map<String, Object> mail = new HashMap<>();
        mail.put("from", "me@localhost");
        mail.put("to", "you@localhost");
        mail.put("message", "Default vars from process arguments.");

        Context ctx = mock(Context.class);
        when(ctx.processConfiguration()).thenReturn(ProcessConfiguration.builder().build());
        when(ctx.workingDirectory()).thenReturn(Paths.get(System.getProperty("user.dir")));
        when(ctx.variables()).thenReturn(new MapBackedVariables(Collections.singletonMap("smtpParams", processArgsDefaults)));
        when(ctx.defaultVariables()).thenReturn(new MapBackedVariables(policyDefaults));

        SmtpTaskV2 t = new SmtpTaskV2(ctx);
        t.execute(new MapBackedVariables(Collections.singletonMap("mail", mail)));

        MimeMessage[] messages = mailServer.getReceivedMessages();
        assertEquals(1, messages.length);
        assertEquals("Default vars from process arguments.\r\n", messages[0].getContent());
    }
}
