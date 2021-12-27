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

import com.google.common.collect.ImmutableMap;
import com.walmartlabs.concord.client.*;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.sdk.Constants;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class KvServiceIT extends AbstractServerIT {

    @Test
    public void testKv() throws Exception {
        String testKey = "key_" + randomString();

        byte[] ab = test("kvInc", "main", testKey);
        assertLog(".*x=[0-9]+.*", ab);
        assertLog(".*abc123.*", ab);
        assertLog(".*Hello, world.*", ab);

        // ---

        ab = test("kvInc", "verify", testKey);
        assertLog(".*Hello again, world.*", ab);

        // ---

        ab = test("kvInc", "verify2", testKey);
        assertLog(".*xyz.*", ab);
    }

    @Test
    public void testKvLong() throws Exception {
        String testKey = "key_" + randomString();

        byte[] ab = test("kvInc", "testLong", testKey);
        assertLog(".*x=1.*", ab);
        assertLog(".*y=1.*", ab);
        assertLog(".*a=2.*", ab);
        assertLog(".*b=2.*", ab);
        assertLog(".*c=234.*", ab);
        assertLog(".*d=235.*", ab);
    }

    @Test
    public void testKvWithSpecialString() throws Exception {
        String testKey = "key_" + randomString();

        byte[] ab = test("kvSpecialString", "default", testKey);

        assertLog(".*" + Pattern.quote("#aaa#bbb") + ".*", ab);
    }

    @Test
    public void testInvalidKeys() throws Exception {
        OrganizationsApi organizationsApi = new OrganizationsApi(getApiClient());
        String orgName = "org_" + randomString();
        organizationsApi.createOrUpdate(new OrganizationEntry()
                .setName(orgName));

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        String projectName = "project_" + randomString();
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setRawPayloadMode(ProjectEntry.RawPayloadModeEnum.OWNERS));

        // ---

        byte[] payload = archive(KvServiceIT.class.getResource("kvInvalidKeys").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("org", orgName);
        input.put("project", projectName);
        StartProcessResponse spr = start(input);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pe = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FAILED, pe.getStatus());

        // ---

        byte[] ab = getLog(pe.getLogFileName());
        assertLogAtLeast(".*Keys cannot be empty or null.*", 1, ab);
    }

    @Test
    public void testCallFromScript() throws Exception {
        OrganizationsApi organizationsApi = new OrganizationsApi(getApiClient());
        String orgName = "org_" + randomString();
        organizationsApi.createOrUpdate(new OrganizationEntry()
                .setName(orgName));

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        String projectName = "project_" + randomString();
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setRawPayloadMode(ProjectEntry.RawPayloadModeEnum.OWNERS));

        // ---

        byte[] payload = archive(KvServiceIT.class.getResource("kvScript").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("org", orgName);
        input.put("project", projectName);
        StartProcessResponse spr = start(input);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pe = waitForCompletion(processApi, spr.getInstanceId());

        // ---

        byte[] ab = getLog(pe.getLogFileName());
        assertLog(".*got myKey: myValue*", ab);
    }

    private byte[] test(String process, String entryPoint, String testKey) throws Exception {
        Map<String, Object> args = ImmutableMap.of("testKey", testKey);
        byte[] payload = createPayload(process, entryPoint, args);

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());

        return getLog(pir.getLogFileName());
    }

    private byte[] createPayload(String process, String entryPoint, Map<String, Object> args) throws Exception {
        Path src = Paths.get(KvServiceIT.class.getResource(process).toURI());

        Path tmpDir = createTempDir();
        IOUtils.copy(src, tmpDir);

        Map<String, Object> req = ImmutableMap.of("entryPoint", entryPoint,
                "arguments", args);

        Path reqFile = tmpDir.resolve(Constants.Files.CONFIGURATION_FILE_NAME);
        try (Writer w = new FileWriter(reqFile.toFile())) {
            getApiClient().getJSON().getGson().toJson(req, w);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(baos)) {
            IOUtils.zip(zip, tmpDir);
        }

        return baos.toByteArray();
    }
}
