package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import com.walmartlabs.concord.server.api.project.CreateProjectResponse;
import com.walmartlabs.concord.server.api.org.project.ProjectEntry;
import com.walmartlabs.concord.server.api.project.ProjectResource;
import com.walmartlabs.concord.server.org.OrganizationManager;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.util.UUID;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.assertNotNull;

public class ProjectInfoIT extends AbstractServerIT {

    @Test(timeout = 30000)
    public void test() throws Exception {
        UUID orgId = OrganizationManager.DEFAULT_ORG_ID;

        String projectName = "project_" + randomString();

        ProjectResource projectResource = proxy(ProjectResource.class);
        CreateProjectResponse cpr = projectResource.createOrUpdate(new ProjectEntry(orgId, projectName));

        String entryPoint = projectName;

        // ---

        byte[] payload = archive(ProjectInfoIT.class.getResource("projectInfo").toURI());

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(entryPoint, new ByteArrayInputStream(payload), null, false, null);
        assertNotNull(spr.getInstanceId());

        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Org ID:.*" + orgId + ".*", ab);
        assertLog(".*Project ID:.*" + cpr.getId() + ".*", ab);
    }
}
