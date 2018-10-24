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
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.sdk.Constants;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.assertEquals;

public class TemplateIT extends AbstractServerIT {

    private static final String META_JS = "({ entryPoint: \"main\", arguments: { greeting: \"Hello, \" + _input.name }})";

    @Test(timeout = 60000)
    public void test() throws Exception {
        final String processYml = "main:\n- expr: ${log.info(\"test\", greeting)}";

        String templateAlias = "template_" + randomString();
        Path templatePath = createTemplate(processYml);

        TemplateAliasApi templateAliasResource = new TemplateAliasApi(getApiClient());
        templateAliasResource.createOrUpdate(new TemplateAliasEntry()
                .setAlias(templateAlias)
                .setUrl(templatePath.toUri().toString()));

        // ---

        String orgName = "Default";
        String projectName = "project_" + randomString();
        String myName = "myName_" + randomString();

        // ---

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        Map<String, Object> cfg = new HashMap<>();
        cfg.put(Constants.Request.TEMPLATE_KEY, templateAlias);
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setCfg(cfg)
                .setAcceptsRawPayload(true));

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        Map<String, Object> input = new HashMap<>();
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("name", myName);
        StartProcessResponse spr = start(input);

        // ---

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Hello, " + myName + ".*", ab);
    }

    @Test(timeout = 60000)
    public void testInputVariablesStillPresent() throws Exception {
        final String processYml = "main:\n- expr: ${log.info(\"test\", xxx)}";

        String templateAlias = "template_" + randomString();
        Path templatePath = createTemplate(processYml);

        TemplateAliasApi templateAliasResource = new TemplateAliasApi(getApiClient());
        templateAliasResource.createOrUpdate(new TemplateAliasEntry()
                .setAlias(templateAlias)
                .setUrl(templatePath.toUri().toString()));

        // ---

        String orgName = "Default";
        String projectName = "project_" + randomString();
        String myName = "myName_" + randomString();

        // ---

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        Map<String, Object> cfg = new HashMap<>();
        cfg.put(Constants.Request.TEMPLATE_KEY, templateAlias);
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setCfg(cfg)
                .setAcceptsRawPayload(true));

        // ---
        Map<String, Object> args = Collections.singletonMap(Constants.Request.ARGUMENTS_KEY,
                Collections.singletonMap("xxx", "BOO"));

        ProcessApi processApi = new ProcessApi(getApiClient());
        Map<String, Object> input = new HashMap<>();
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("name", myName);
        input.put("request", args);
        StartProcessResponse spr = start(input);

        // ---

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*BOO.*", ab);
    }

    private static Path createTemplate(String process) throws IOException {
        Path tmpDir = createTempDir();

        Path metaPath = tmpDir.resolve("_main.js");
        Files.write(metaPath, META_JS.getBytes());

        Path processesPath = tmpDir.resolve("flows");
        Files.createDirectories(processesPath);

        Path procPath = processesPath.resolve("hello.yml");
        Files.write(procPath, process.getBytes());

        Path tmpZip = createTempFile(".zip");
        try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(Files.newOutputStream(tmpZip))) {
            IOUtils.zip(zip, tmpDir);
        }

        return tmpZip;
    }
}
