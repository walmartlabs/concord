package com.walmartlabs.concord.it.runtime.v2;

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

import ca.ibodrov.concord.testcontainers.ConcordProcess;
import ca.ibodrov.concord.testcontainers.ContainerListener;
import ca.ibodrov.concord.testcontainers.ContainerType;
import ca.ibodrov.concord.testcontainers.Payload;
import ca.ibodrov.concord.testcontainers.junit5.ConcordRule;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetup;
import com.walmartlabs.concord.client2.ProcessEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.Testcontainers;

import javax.mail.internet.MimeMessage;

import static com.walmartlabs.concord.it.runtime.v2.Utils.resourceToString;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SmtpIT extends AbstractTest {

    @RegisterExtension
    GreenMailExtension mailServer = new GreenMailExtension(new ServerSetup(0, "0.0.0.0", ServerSetup.PROTOCOL_SMTP));

    @RegisterExtension
    public final ConcordRule concord = ConcordConfiguration.configure()
            .containerListener(new ContainerListener() {
                @Override
                public void beforeStart(ContainerType type) {
                    // use container listener to expose the SMTP server's port right before the container starts
                    if (type == ContainerType.AGENT) {
                        Testcontainers.exposeHostPorts(mailServer.getSmtp().getPort());
                    }
                }
            });

    @Test
    public void test() throws Exception {
        String concordYml = resourceToString(ProcessIT.class.getResource("smtp/concord.yml"));

        // SMTP host and port must be accessible by the process
        // i.e. when running in a container the host must point to the docker host's address
        concordYml = concordYml.replaceAll("PROJECT_VERSION", ITConstants.PROJECT_VERSION)
                .replaceAll("SMTP_HOST", concord.hostAddressAccessibleByContainers())
                .replaceAll("SMTP_PORT", String.valueOf(mailServer.getSmtp().getPort()));

        Payload payload = new Payload().concordYml(concordYml);
        ConcordProcess proc = concord.processes().start(payload);

        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);
        proc.assertLog(".*Done!.*");

        // ---

        MimeMessage[] messages = mailServer.getReceivedMessages();
        assertEquals(1, messages.length);

        MimeMessage msg = messages[0];
        assertEquals("Hey! How are you?\r\n", msg.getContent());
        assertEquals("me@localhost", msg.getFrom()[0].toString());
    }
}
