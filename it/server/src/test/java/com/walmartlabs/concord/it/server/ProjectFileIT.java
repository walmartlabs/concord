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
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.sdk.Constants;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ProjectFileIT extends AbstractServerIT {

    @Test
    public void testSingleProfile() throws Exception {
        simpleTest("projectfile/singleprofile", ".*Hello, world.*", ".*xyz54321abc.*");
    }

    @Test
    public void testSingleProfileUsingConfiguration() throws Exception {
        simpleTest("projectfile/singleprofilecfg", ".*Hello, world.*", ".*54321.*");
    }

    @Test
    public void testExternalProfile() throws Exception {
        simpleTest("projectfile/externalprofile", ".*Hello, world.*");
    }

    @Test
    public void testAltName() throws Exception {
        simpleTest("projectfile/altname", ".*Hello, world.*");
    }

    @Test
    public void testOverrideFlow() throws Exception {
        simpleTest("projectfile/overrideflow", ".*Hello, world.*");
    }

    @Test
    public void testExpressionsInVariables() throws Exception {
        simpleTest("projectfile/expr");
    }

    @Test
    public void testExternalScript() throws Exception {
        simpleTest("projectfile/externalscript", ".*hello!.*", ".*bye!.*");
    }

    @Test
    public void testExternalScriptWithErrorBlock() throws Exception {
        simpleTest("projectfile/scriptWithErrorBlock", ".*error occurred!.*");
    }

    @Test
    public void testDependencies() throws Exception {
        String dep = "file:///" + ITConstants.DEPENDENCIES_DIR + "/example.jar";
        Path tmpDir = createTempDir();

        // prepare .concord.yml
        try (InputStream in = ProjectFileIT.class.getResourceAsStream("projectfile/deps/.template.yml");
             BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {

            List<String> l = new ArrayList<>();

            String line;
            while ((line = reader.readLine()) != null) {
                l.add(line.replaceAll("WILL_BE_REPLACED", dep));
            }

            Path p = tmpDir.resolve(".concord.yml");
            Files.write(p, l);
        }

        // create the payload

        String request = "{ \"entryPoint\": \"main\" }";
        Path requestFile = tmpDir.resolve(Constants.Files.CONFIGURATION_FILE_NAME);
        Files.write(requestFile, Collections.singletonList(request));

        Path src = Paths.get(DependenciesIT.class.getResource("projectfile/deps").toURI());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(baos)) {
            IOUtils.zip(zip, src);
            IOUtils.zip(zip, tmpDir);
        }

        byte[] payload = baos.toByteArray();

        // send the request

        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        ProcessEntry psr = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, psr.getStatus());

        // ---

        byte[] ab = getLog(psr.getInstanceId());
        assertLog(".*Hello!.*", ab);
    }

    @Test
    public void testArchiveOverride() throws Exception {
        String orgName = "Default";

        // ---

        String projectName = "project_" + randomString();
        String repoName = "repo_" + randomString();
        String secretName = "secret_" + randomString();

        generateKeyPair(orgName, secretName, false, null);

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE)
                .repositories(Collections.singletonMap(repoName, new RepositoryEntry()
                        .name(repoName)
                        .url(createRepo("repositoryRefresh"))
                        .branch("master")
                        .secretName(secretName))));

        // ---

        byte[] payload = archive(ProjectFileIT.class.getResource("projectfile/singleprofile").toURI());

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("org", orgName);
        input.put("project", projectName);
        StartProcessResponse spr = start(input);
        assertNotNull(spr.getInstanceId());

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        // ---

        byte[] ab = getLog(pir.getInstanceId());

        assertLog(".*54321.*", ab);
    }

    @Test
    public void testExpressionScriptName() throws Exception {
        simpleTest("projectfile/expressionscript", ".*hello!.*", ".*bye!.*");
    }

    private void simpleTest(String resource, String... logPatterns) throws Exception {
        byte[] payload = archive(ProjectFileIT.class.getResource(resource).toURI());

        // ---

        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        // ---

        if (logPatterns == null || logPatterns.length == 0) {
            return;
        }

        byte[] ab = getLog(pir.getInstanceId());

        for (String p : logPatterns) {
            assertLog(p, ab);
        }
    }
}
