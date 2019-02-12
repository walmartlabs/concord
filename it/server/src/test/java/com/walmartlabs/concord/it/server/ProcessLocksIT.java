package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.client.*;
import com.walmartlabs.concord.client.ProcessEntry.StatusEnum;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.waitForStatus;
import static org.junit.Assert.assertEquals;

public class ProcessLocksIT extends AbstractServerIT {

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testOrgScope() throws Exception {
        String orgName = "org_" + randomString();
        String projectName = "project_" + randomString();

        OrganizationsApi organizationsApi = new OrganizationsApi(getApiClient());
        organizationsApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate(orgName, new ProjectEntry().setName(projectName)
                .setAcceptsRawPayload(true));

        // ---

        byte[] payload = archive(ProcessIT.class.getResource("processLocks").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("archive", payload);

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse sprA = start(input);

        ProcessEntry pirA = waitForStatus(processApi, sprA.getInstanceId(), StatusEnum.FAILED, StatusEnum.RUNNING);
        assertEquals(StatusEnum.RUNNING, pirA.getStatus());
        waitForLog(pirA.getLogFileName(), ".*locked!.*");

        // ---

        input = new HashMap<>();
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("archive", payload);

        StartProcessResponse sprB = start(input);

        ProcessEntry pirB = waitForStatus(processApi, sprB.getInstanceId(), StatusEnum.FAILED, StatusEnum.SUSPENDED);
        assertEquals(StatusEnum.SUSPENDED, pirB.getStatus());

        // ---

        pirA = waitForStatus(processApi, sprA.getInstanceId(), StatusEnum.FAILED, StatusEnum.FINISHED);
        assertEquals(StatusEnum.FINISHED, pirA.getStatus());

        pirB = waitForStatus(processApi, sprB.getInstanceId(), StatusEnum.FAILED, StatusEnum.FINISHED);
        assertEquals(StatusEnum.FINISHED, pirB.getStatus());
    }
}
