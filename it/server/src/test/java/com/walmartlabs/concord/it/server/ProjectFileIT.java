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

import com.googlecode.junittoolbox.ParallelRunner;
import com.walmartlabs.concord.client.*;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.sdk.Constants;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;

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
import static org.junit.Assert.*;

@RunWith(ParallelRunner.class)
public class ProjectFileIT extends AbstractServerIT {

    @Test(timeout = 60000)
    public void testSingleProfile() throws Exception {
        simpleTest("projectfile/singleprofile", ".*Hello, world.*", ".*xyz54321abc.*");
    }

    @Test(timeout = 60000)
    public void testSingleProfileUsingConfiguration() throws Exception {
        simpleTest("projectfile/singleprofilecfg", ".*Hello, world.*", ".*54321.*");
    }

    @Test(timeout = 60000)
    public void testExternalProfile() throws Exception {
        simpleTest("projectfile/externalprofile", ".*Hello, world.*");
    }

    @Test(timeout = 60000)
    public void testAltName() throws Exception {
        simpleTest("projectfile/altname", ".*Hello, world.*");
    }

    @Test(timeout = 60000)
    public void testOverrideFlow() throws Exception {
        simpleTest("projectfile/overrideflow", ".*Hello, world.*");
    }

    @Test(timeout = 60000)
    public void testExpressionsInVariables() throws Exception {
        simpleTest("projectfile/expr");
    }

    @Test(timeout = 60000)
    public void testExternalScript() throws Exception {
        simpleTest("projectfile/externalscript", ".*hello!.*", ".*bye!.*");
    }

    @Test(timeout = 60000)
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
        Path requestFile = tmpDir.resolve(Constants.Files.REQUEST_DATA_FILE_NAME);
        Files.write(requestFile, Collections.singletonList(request));

        Path src = Paths.get(DependenciesIT.class.getResource("projectfile/deps").toURI());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(baos)) {
            IOUtils.zip(zip, src);
            IOUtils.zip(zip, tmpDir);
        }

        byte[] payload = baos.toByteArray();

        // send the request

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        ProcessEntry psr = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, psr.getStatus());

        // ---

        byte[] ab = getLog(psr.getLogFileName());
        assertLog(".*Hello!.*", ab);
    }

    @Test(timeout = 60000)
    public void testArchiveOverride() throws Exception {
        String orgName = "Default";

        // ---

        String projectName = "project_" + randomString();
        String repoName = "repo_" + randomString();
        String repoUrl = "git@test_" + randomString();
        String secretName = "secret_" + randomString();

        generateKeyPair(orgName, secretName, false, null);

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setAcceptsRawPayload(true)
                .setRepositories(Collections.singletonMap(repoName, new RepositoryEntry()
                        .setName(repoName)
                        .setUrl(repoUrl)
                        .setBranch("master")
                        .setSecretName(secretName))));

        // ---

        byte[] payload = archive(ProcessIT.class.getResource("projectfile/singleprofile").toURI());

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("org", orgName);
        input.put("project", projectName);
        StartProcessResponse spr = start(input);
        assertNotNull(spr.getInstanceId());

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());

        // ---

        byte[] ab = getLog(pir.getLogFileName());

        assertLog(".*54321.*", ab);
    }

    @Test(timeout = 60000)
    public void testArchiveOverrideSync() throws Exception {
        String orgName = "Default";

        // ---

        String projectName = "project_" + randomString();
        String repoName = "repo_" + randomString();
        String repoUrl = "git@test_" + randomString();
        String secretName = "secret_" + randomString();

        generateKeyPair(orgName, secretName, false, null);

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setAcceptsRawPayload(true)
                .setRepositories(Collections.singletonMap(repoName, new RepositoryEntry()
                        .setName(repoName)
                        .setUrl(repoUrl)
                        .setBranch("master")
                        .setSecretName(secretName))));

        // ---

        byte[] payload = archive(ProcessIT.class.getResource("projectfile/singleprofile-sync").toURI());

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("sync", true);
        StartProcessResponse spr = start(input);
        assertNotNull(spr.getInstanceId());

        ProcessEntry pir = processApi.get(spr.getInstanceId());

        // ---
        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*100223.*", ab);
        assertLog(".*Boo Zoo.*", ab);
        assertLog(".*1000022.*", ab);
        assertLog(".*100323.*", ab);
        assertLog(".*r3d.*", ab);

        assertSame(pir.getStatus(), ProcessEntry.StatusEnum.FINISHED);
    }

    private void simpleTest(String resource, String... logPatterns) throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource(resource).toURI());

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());

        // ---

        if (logPatterns == null || logPatterns.length == 0) {
            return;
        }

        byte[] ab = getLog(pir.getLogFileName());

        for (String p : logPatterns) {
            assertLog(p, ab);
        }
    }
}
