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

import com.icegreen.greenmail.junit.GreenMailRule;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.MapBackedVariables;
import org.junit.Rule;
import org.junit.Test;

import javax.mail.internet.MimeMessage;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SmtpTaskV2Test {

    @Rule
    public final GreenMailRule mailServer = new GreenMailRule(ServerSetupTest.SMTP);

    @Test
    public void test() throws Exception {
        int port = mailServer.getSmtp().getPort();

        Map<String, Object> smtpParams = new HashMap<>();
        smtpParams.put("host", "localhost");
        smtpParams.put("port", port);

        Map<String, Object> mail = new HashMap<>();
        mail.put("from", "me@localhost");
        mail.put("to", "you@localhost");
        mail.put("message", "Hello!");

        Context ctx = mock(Context.class);
        when(ctx.workingDirectory()).thenReturn(Paths.get(System.getProperty("user.dir")));
        when(ctx.variables()).thenReturn(new MapBackedVariables(Collections.singletonMap("smtpParams", smtpParams)));

        SmtpTaskV2 t = new SmtpTaskV2(ctx);
        t.execute(new MapBackedVariables(Collections.singletonMap("mail", mail)));

        MimeMessage[] messages = mailServer.getReceivedMessages();
        assertEquals(1, messages.length);
        assertEquals("Hello!\r\n", messages[0].getContent());

        mailServer.reset();
    }
}
