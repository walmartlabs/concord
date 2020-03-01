package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.client.OrganizationEntry;
import com.walmartlabs.concord.client.OrganizationsApi;
import com.walmartlabs.concord.client.ProcessEntry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Path;
import java.util.Map;

public class OneOpsTriggerITV2 extends AbstractOneOpsTriggerIT {

    protected static final long DEFAULT_TEST_TIMEOUT = 120000;

    private OrganizationsApi orgApi;
    private String orgName;
    private String projectName;
    private String repoName;

    @Before
    public void setup() throws Exception {
        orgApi = new OrganizationsApi(getApiClient());
        orgName = "org_" + randomString();
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        Path repo = initRepo("oneopsTests/trigger");
        projectName = "project_" + randomString();
        repoName = "repo_" + randomString();
        initProjectAndRepo(orgName, projectName, repoName, null, repo);
        refreshRepo(orgName, projectName, repoName);

    }

    @After
    public void tearDown() throws ApiException {
        orgApi.delete(orgName, "yes");
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testOneOpsTriggerv2() throws Exception {
        sendOneOpsEvent("oneopsTests/events/oneops_deployment_qa.json");

        waitForTriggers(orgName, projectName, repoName, 2);

        Map<ProcessEntry.StatusEnum, ProcessEntry> ps = waitProcesses(orgName, projectName, ProcessEntry.StatusEnum.FINISHED);
        assertProcessLog(ps.get(ProcessEntry.StatusEnum.FINISHED), ".*Oneops has completed a deployment trigger version 2*");
    }
}
