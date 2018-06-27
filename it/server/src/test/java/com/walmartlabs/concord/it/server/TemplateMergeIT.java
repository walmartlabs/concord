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
import com.walmartlabs.concord.sdk.Constants;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.assertEquals;

public class TemplateMergeIT extends AbstractServerIT {

    @Test(timeout = 60000)
    public void test() throws Exception {
        Path template = createTemplate();
        byte[] payload = archive(TemplateMergeIT.class.getResource("templateMerge/process").toURI());

        // ---

        String orgName = "Default";
        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setCfg(Collections.singletonMap(Constants.Request.TEMPLATE_KEY, template.toUri().toString()))
                .setAcceptsRawPayload(true));

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("archive", payload);
        StartProcessResponse spr = start(input);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Hello, Concord.*", ab);
    }

    private static Path createTemplate() throws Exception {
        byte[] ab = archive(TemplateMergeIT.class.getResource("templateMerge/template").toURI());
        Path p = createTempFile(".zip");
        Files.write(p, ab);
        return p;
    }
}
