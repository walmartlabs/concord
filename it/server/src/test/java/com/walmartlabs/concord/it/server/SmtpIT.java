package com.walmartlabs.concord.it.server;

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
import com.icegreen.greenmail.util.ServerSetup;
import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.sdk.Constants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.mail.internet.MimeMessage;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SmtpIT extends AbstractServerIT {

    @RegisterExtension
    GreenMailExtension mail = new GreenMailExtension(new ServerSetup(0, "0.0.0.0", ServerSetup.PROTOCOL_SMTP));

    @Test
    public void testSimple() throws Exception {
        URI dir = SmtpIT.class.getResource("smtp").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // --

        String orgName = "Default";
        String projectName = "project_" + randomString();

        Map<String, Object> smtpParams = new HashMap<>();
        smtpParams.put("host", ITConstants.SMTP_SERVER_HOST);
        smtpParams.put("port", mail.getSmtp().getPort());

        Map<String, Object> args = new HashMap<>();
        args.put("smtpParams", smtpParams);

        Map<String, Object> cfg = new HashMap<>();
        cfg.put(Constants.Request.ARGUMENTS_KEY, args);

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .cfg(cfg)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        // --

        Map<String, Object> input = new HashMap<>();
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("archive", payload);
        StartProcessResponse spr = start(input);

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        MimeMessage[] messages = mail.getReceivedMessages();
        assertNotNull(messages);
        assertEquals(1, messages.length);

        MimeMessage msg = messages[0];
        assertEquals("hi!\r\n", msg.getContent());
        assertEquals("me@localhost", msg.getFrom()[0].toString());
    }
}
