package com.walmartlabs.concord.it.server;

import com.icegreen.greenmail.junit.GreenMailRule;
import com.icegreen.greenmail.util.ServerSetup;
import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import com.walmartlabs.concord.server.api.org.project.ProjectEntry;
import com.walmartlabs.concord.server.api.project.ProjectResource;
import org.junit.Rule;
import org.junit.Test;

import javax.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class SmtpIT extends AbstractServerIT {

    @Rule
    public final GreenMailRule mail = new GreenMailRule(new ServerSetup(0, "0.0.0.0", ServerSetup.PROTOCOL_SMTP));

    @Test(timeout = 30000)
    public void testSimple() throws Exception {
        URI dir = SmtpIT.class.getResource("smtp").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // --

        String projectName = "project_" + randomString();

        Map<String, Object> smtpParams = new HashMap<>();
        smtpParams.put("host", ITConstants.SMTP_SERVER_HOST);
        smtpParams.put("port", mail.getSmtp().getPort());

        Map<String, Object> args = new HashMap<>();
        args.put("smtpParams", smtpParams);

        Map<String, Object> cfg = new HashMap<>();
        cfg.put(InternalConstants.Request.ARGUMENTS_KEY, args);

        ProjectResource projectResource = proxy(ProjectResource.class);
        projectResource.createOrUpdate(new ProjectEntry(null, projectName, null, null, null, null, cfg, null, null));

        // --

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(projectName, new ByteArrayInputStream(payload), null, false, null);

        // ---

        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());
        assertEquals(ProcessStatus.FINISHED, pir.getStatus());

        // ---

        MimeMessage[] messages = mail.getReceivedMessages();
        assertNotNull(messages);
        assertEquals(1, messages.length);

        MimeMessage msg = messages[0];
        assertEquals("hi!\r\n", msg.getContent());
        assertEquals("me@localhost", msg.getFrom()[0].toString());
    }
}
