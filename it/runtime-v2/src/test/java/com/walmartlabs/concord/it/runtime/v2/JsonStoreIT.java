package com.walmartlabs.concord.it.runtime.v2;

import ca.ibodrov.concord.testcontainers.ConcordProcess;
import ca.ibodrov.concord.testcontainers.Payload;
import ca.ibodrov.concord.testcontainers.junit4.ConcordRule;
import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.client.*;
import org.junit.Rule;
import org.junit.Test;

import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.randomString;
import static com.walmartlabs.concord.it.runtime.v2.ITConstants.DEFAULT_TEST_TIMEOUT;
import static org.junit.Assert.*;

public class JsonStoreIT {

    @Rule
    public final ConcordRule concord = ConcordConfiguration.configure();

    /**
     * Tests various methods of the 'jsonStore' plugin.
     */
    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void test() throws Exception {
        ApiClient apiClient = concord.apiClient();

        String orgName = "org_" + randomString();
        OrganizationsApi orgApi = new OrganizationsApi(apiClient);
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        String projectName = "project_" + randomString();
        ProjectsApi projectsApi = new ProjectsApi(apiClient);
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setRawPayloadMode(ProjectEntry.RawPayloadModeEnum.OWNERS));

        String storeName = "store_" + randomString();
        JsonStoreApi jsonStoreApi = new JsonStoreApi(apiClient);
        jsonStoreApi.createOrUpdate(orgName, new JsonStoreRequest()
                .setName(storeName));

        // ---

        Payload payload = new Payload()
                .org(orgName)
                .project(projectName)
                .archive(ProcessIT.class.getResource("jsonStore").toURI())
                .arg("storeName", storeName);

        ConcordProcess proc = concord.processes().start(payload);

        ProcessEntry pe = proc.waitForStatus(ProcessEntry.StatusEnum.FINISHED);
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pe.getStatus());

        proc.assertLog(".*empty: ==.*");
        proc.assertLog(".*get: \\{x=1}.*");
        proc.assertLog(".*Updating item 'test'.*" + orgName + ".*" + storeName + ".*");

        // ---

        JsonStoreDataApi jsonStoreDataApi = new JsonStoreDataApi(apiClient);
        Object test = jsonStoreDataApi.get(orgName, storeName, "test");
        assertNotNull(test);
        assertTrue(test instanceof Map);

        Map<String, Object> m = (Map<String, Object>) test;
        assertEquals(m.get("x"), "1");
    }
}
