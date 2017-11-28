package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import com.walmartlabs.concord.server.api.project.CreateProjectResponse;
import com.walmartlabs.concord.server.api.project.DeleteProjectResponse;
import com.walmartlabs.concord.server.api.org.project.ProjectEntry;
import com.walmartlabs.concord.server.api.project.ProjectResource;
import org.junit.Test;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import java.io.ByteArrayInputStream;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.*;

public class ProjectDeleteIT extends AbstractServerIT {

    @Test(timeout = 30000)
    public void test() throws Exception {
        String projectName = "project_" + randomString();

        ProjectResource projectResource = proxy(ProjectResource.class);
        CreateProjectResponse cpr = projectResource.createOrUpdate(new ProjectEntry(projectName));
        assertTrue(cpr.isOk());

        // ---

        byte[] payload = archive(ProjectDeleteIT.class.getResource("simple").toURI());

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(projectName, new ByteArrayInputStream(payload), null, false, null);

        // ---

        ProcessEntry pe = waitForCompletion(processResource, spr.getInstanceId());
        assertEquals(ProcessStatus.FINISHED, pe.getStatus());
        assertEquals(cpr.getId(), pe.getProjectId());

        // ---

        DeleteProjectResponse dpr = projectResource.delete(projectName);
        assertTrue(dpr.isOk());

        try {
            projectResource.get(projectName);
            fail("Should fail");
        } catch (NotFoundException | BadRequestException e) {
        }

        // ---

        pe = processResource.get(spr.getInstanceId());
        assertNull(pe.getProjectId());
    }
}
