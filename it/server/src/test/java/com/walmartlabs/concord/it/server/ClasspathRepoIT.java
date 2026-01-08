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

import com.walmartlabs.concord.client2.*;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static com.walmartlabs.concord.client2.ProjectEntry.RawPayloadModeEnum.DISABLED;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClasspathRepoIT extends AbstractServerIT {

    @Test
    public void test() throws Exception {
        String url = "classpath://com/walmartlabs/concord/server/selfcheck/concord.yml";
        String projectName = "project_" + randomString();
        String repoName = "repo_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject("Default", new ProjectEntry()
                .name(projectName)
                .rawPayloadMode(DISABLED)
                .repositories(Collections.singletonMap(repoName, new RepositoryEntry()
                        .name(repoName)
                        .url(url)
                        .branch("master"))));

        // ---

        StartProcessResponse spr = start("Default", projectName, repoName, null, null);
        assertTrue(spr.getOk());

        // ---

        ProcessEntry pe = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pe.getStatus());

        // ---

        byte[] ab = getLog(pe.getInstanceId());
        assertLog(".*OK.*", ab);
    }
}
