package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;

public class ProjectTaskIT extends AbstractServerIT {

    @Test(timeout = 60000)
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

        ProcessResource processResource = proxy(ProcessResource.class);
        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());

        // ---

        byte[] ab = getLog(pir.getLogFileName());

        assertLog(".*CREATED.*", ab);
        assertLog(".*Done!.*", ab);
    }
}
