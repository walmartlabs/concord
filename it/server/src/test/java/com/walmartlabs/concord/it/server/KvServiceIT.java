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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.assertNotNull;

public class KvServiceIT extends AbstractServerIT {

    @Test(timeout = 60000)
    public void testKv() throws Exception {
        String testKey = "key_" + randomString();

        byte[] ab = test("main", testKey);
        assertLog(".*x=[0-9]+.*", ab);
        assertLog(".*abc123.*", ab);
        assertLog(".*Hello, world.*", ab);

        // ---

        ab = test("verify", testKey);
        assertLog(".*Hello again, world.*", ab);

        // ---

        ab = test("verify2", testKey);
        assertLog(".*xyz.*", ab);
    }

    @Test(timeout = 60000)
    public void testKvLong() throws Exception {
        String testKey = "key_" + randomString();

        byte[] ab = test("testLong", testKey);
        assertLog(".*x=1.*", ab);
        assertLog(".*y=1.*", ab);
        assertLog(".*a=2.*", ab);
        assertLog(".*b=2.*", ab);
        assertLog(".*c=234.*", ab);
        assertLog(".*d=235.*", ab);
    }

    private byte[] test(String entryPoint, String testKey) throws Exception {
        Map<String, Object> args = ImmutableMap.of("testKey", testKey);
        byte[] payload = createPayload(entryPoint, args);

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), null, false, null);
        assertNotNull(spr.getInstanceId());

        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());

        return getLog(pir.getLogFileName());
    }

    private static byte[] createPayload(String entryPoint, Map<String, Object> args) throws Exception {
        Path src = Paths.get(KvServiceIT.class.getResource("kvInc").toURI());

        Path tmpDir = createTempDir();
        IOUtils.copy(src, tmpDir);

        Map<String, Object> req = ImmutableMap.of("entryPoint", entryPoint,
                "arguments", args);

        Path reqFile = tmpDir.resolve(InternalConstants.Files.REQUEST_DATA_FILE_NAME);
        ObjectMapper om = new ObjectMapper();
        om.writeValue(reqFile.toFile(), req);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(baos)) {
            IOUtils.zip(zip, tmpDir);
        }

        return baos.toByteArray();
    }
}
