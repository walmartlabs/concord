package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import com.walmartlabs.concord.server.api.project.ProjectEntry;
import com.walmartlabs.concord.server.api.project.ProjectResource;
import com.walmartlabs.concord.server.api.project.RepositoryEntry;
import org.junit.Test;

import java.util.Collections;

import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ClasspathRepoIT extends AbstractServerIT {

    @Test(timeout = 30000)
    public void test() throws Exception {
        String url = "classpath://com/walmartlabs/concord/server/selfcheck/concord.yml";
        String projectName = "project_" + System.currentTimeMillis();
        String repoName = "repo_" + System.currentTimeMillis();

        ProjectResource projectResource = proxy(ProjectResource.class);
        projectResource.createOrUpdate(new ProjectEntry(projectName,
                Collections.singletonMap(repoName, new RepositoryEntry(repoName, url))));

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(projectName + ":" + repoName, null, false, null);
        assertTrue(spr.isOk());

        // ---

        ProcessEntry pe = waitForCompletion(processResource, spr.getInstanceId());
        assertEquals(ProcessStatus.FINISHED, pe.getStatus());

        // ---

        byte[] ab = getLog(pe.getLogFileName());
        assertLog(".*OK.*", ab);
    }
}
