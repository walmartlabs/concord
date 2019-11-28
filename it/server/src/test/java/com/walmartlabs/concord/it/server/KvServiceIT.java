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
import com.walmartlabs.concord.client.ProcessApi;
import com.walmartlabs.concord.client.ProcessEntry;
import com.walmartlabs.concord.client.StartProcessResponse;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.sdk.Constants;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.regex.Pattern;

import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.assertNotNull;

public class KvServiceIT extends AbstractServerIT {

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
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

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
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

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testKvWithSpecialString() throws Exception {
        String testKey = "key_" + randomString();

        byte[] ab = test("kvSpecialString", "default", testKey);

        assertLog(".*" + Pattern.quote("#aaa#bbb") + ".*", ab);
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

        Path reqFile = tmpDir.resolve(Constants.Files.REQUEST_DATA_FILE_NAME);
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
