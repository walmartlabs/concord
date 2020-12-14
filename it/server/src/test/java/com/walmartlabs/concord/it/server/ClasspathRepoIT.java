package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.walmartlabs.concord.client.*;
import org.junit.Test;

import java.util.Collections;

import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ClasspathRepoIT extends AbstractServerIT {

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void test() throws Exception {
        String url = "classpath://com/walmartlabs/concord/server/selfcheck/concord.yml";
        String projectName = "project_" + randomString();
        String repoName = "repo_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate("Default", new ProjectEntry()
                .setName(projectName)
                .setAcceptsRawPayload(false)
                .setRepositories(Collections.singletonMap(repoName, new RepositoryEntry()
                        .setName(repoName)
                        .setUrl(url)
                        .setBranch(DEFAULT_TEST_BRANCH_NAME))));

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start("Default", projectName, repoName, null, null);
        assertTrue(spr.isOk());

        // ---

        ProcessEntry pe = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pe.getStatus());

        // ---

        byte[] ab = getLog(pe.getLogFileName());
        assertLog(".*OK.*", ab);
    }
}
