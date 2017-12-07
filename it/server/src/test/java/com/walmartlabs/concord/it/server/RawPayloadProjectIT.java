package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.server.api.org.project.ProjectEntry;
import com.walmartlabs.concord.server.api.org.project.ProjectResource;
import com.walmartlabs.concord.server.api.process.ProcessResource;
import org.junit.Test;

import javax.ws.rs.BadRequestException;
import java.io.ByteArrayInputStream;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static org.junit.Assert.fail;

public class RawPayloadProjectIT extends AbstractServerIT {

    @Test(timeout = 30000)
    public void testReject() throws Exception {
        ProjectResource projectResource = proxy(ProjectResource.class);

        String orgName = "Default";
        String projectName = "project_" + System.currentTimeMillis();
        projectResource.createOrUpdate(orgName, new ProjectEntry(null, projectName, null, null, null, null, null, null, null, false));

        // ---

        byte[] payload = archive(ProcessIT.class.getResource("example").toURI());

        ProcessResource processResource = proxy(ProcessResource.class);
        try {
            processResource.start(projectName, new ByteArrayInputStream(payload), null, false, null);
            fail("should fail");
        } catch (BadRequestException e) {
        }
    }
}
