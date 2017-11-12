package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import com.walmartlabs.concord.server.api.project.CreateProjectResponse;
import com.walmartlabs.concord.server.api.project.ProjectEntry;
import com.walmartlabs.concord.server.api.project.ProjectResource;
import com.walmartlabs.concord.server.api.team.CreateTeamResponse;
import com.walmartlabs.concord.server.api.team.TeamEntry;
import com.walmartlabs.concord.server.api.team.TeamResource;
import org.junit.Test;

import java.io.ByteArrayInputStream;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.assertNotNull;

public class ProjectInfoIT extends AbstractServerIT {

    @Test(timeout = 30000)
    public void test() throws Exception {
        String teamName = "team_" + randomString();
        String projectName = "project_" + randomString();

        TeamResource teamResource = proxy(TeamResource.class);
        CreateTeamResponse ctr = teamResource.createOrUpdate(new TeamEntry(teamName));

        ProjectResource projectResource = proxy(ProjectResource.class);
        CreateProjectResponse cpr = projectResource.createOrUpdate(new ProjectEntry(teamName, projectName));

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
        assertLog(".*Team ID:.*" + ctr.getId() + ".*", ab);
        assertLog(".*Project ID:.*" + cpr.getId() + ".*", ab);
    }
}
