package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.client.ProcessApi;
import com.walmartlabs.concord.client.ProcessEntry;
import com.walmartlabs.concord.client.StartProcessResponse;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;

public class ProjectTaskIT extends AbstractServerIT {

    @Test
    public void testCreate() throws Exception {
        String orgName = "Default";

        // ---

        String projectName = "project_" + System.currentTimeMillis();
        String repoName = "repo_" + System.currentTimeMillis();
        String repoUrl = "git://127.0.0.1/test.git";
        String repoSecret = "secret_" + System.currentTimeMillis();
        addUsernamePassword(orgName, repoSecret, false, null, "user_" + System.currentTimeMillis(), "pwd_" + System.currentTimeMillis());

        // ---

        byte[] payload = archive(ProcessIT.class.getResource("projectTask").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);

        Map<String, Object> args = new HashMap<>();
        args.put("projectName", projectName);
        args.put("repoName", repoName);
        args.put("repoUrl", repoUrl);
        args.put("repoSecret", repoSecret);

        input.put("request", Collections.singletonMap("arguments", args));

        // ---

        StartProcessResponse spr = start(input);

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());

        // ---

        byte[] ab = getLog(pir.getLogFileName());

        assertLog(".*CREATED.*", ab);
        assertLog(".*Done!.*", ab);
    }
}
