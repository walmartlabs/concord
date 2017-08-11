package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.project.Constants;
import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import com.walmartlabs.concord.server.api.project.ProjectEntry;
import com.walmartlabs.concord.server.api.project.ProjectResource;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.assertEquals;

public class TemplateMergeIT extends AbstractServerIT {

    @Test(timeout = 30000)
    public void test() throws Exception {
        Path template = createTemplate();
        byte[] payload = archive(TemplateMergeIT.class.getResource("templateMerge/process").toURI());

        // ---

        String projectName = "project_" + System.currentTimeMillis();

        ProjectResource projectResource = proxy(ProjectResource.class);
        projectResource.createOrUpdate(new ProjectEntry(projectName, null, null,
                Collections.singletonMap(Constants.Request.TEMPLATE_KEY, template.toUri().toString())));

        // ---

        StartProcessResponse spr = start(projectName, Collections.singletonMap("archive", payload));

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());
        assertEquals(ProcessStatus.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Hello, Concord.*", ab);
    }

    private static Path createTemplate() throws Exception {
        byte[] ab = archive(TemplateMergeIT.class.getResource("templateMerge/template").toURI());
        Path p = Files.createTempFile("template", ".zip");
        Files.write(p, ab);
        return p;
    }
}
