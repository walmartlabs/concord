package com.walmartlabs.concord.it.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.project.Constants;
import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.zip.ZipOutputStream;

import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.assertNotNull;

public class KvServiceIT extends AbstractServerIT {

    @Test(timeout = 30000)
    public void testKv() throws Exception {
        String testKey = "key_" + System.currentTimeMillis();

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

    private byte[] test(String entryPoint, String testKey) throws Exception {
        Map<String, Object> args = ImmutableMap.of("testKey", testKey);
        byte[] payload = createPayload(entryPoint, args);

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), null, false);
        assertNotNull(spr.getInstanceId());

        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());

        return getLog(pir.getLogFileName());
    }

    private static byte[] createPayload(String entryPoint, Map<String, Object> args) throws Exception {
        Path src = Paths.get(KvServiceIT.class.getResource("kvInc").toURI());

        Path tmpDir = Files.createTempDirectory("test");
        IOUtils.copy(src, tmpDir);

        Map<String, Object> req = ImmutableMap.of("entryPoint", entryPoint,
                "arguments", args);

        Path reqFile = tmpDir.resolve(Constants.Files.REQUEST_DATA_FILE_NAME);
        ObjectMapper om = new ObjectMapper();
        om.writeValue(reqFile.toFile(), req);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(baos)) {
            IOUtils.zip(zip, tmpDir);
        }

        return baos.toByteArray();
    }
}
