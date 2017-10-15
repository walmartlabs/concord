package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.common.IOUtils;
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
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;

import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.assertNotNull;

public class FilePermissionsIT extends AbstractServerIT {

    @Test(timeout = 30000)
    public void test() throws Exception {
        Path src = Paths.get(FilePermissionsIT.class.getResource("filePerm").toURI());

        Path tmpDir = Files.createTempDirectory("test");
        IOUtils.copy(src, tmpDir);

        Path testFile = tmpDir.resolve("test.sh");
        Set<PosixFilePermission> permissions = new HashSet<>(Files.getPosixFilePermissions(testFile));
        permissions.add(PosixFilePermission.OWNER_EXECUTE);
        Files.setPosixFilePermissions(testFile, permissions);

        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(payload)) {
            IOUtils.zip(zip, tmpDir);
        }

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload.toByteArray()), null, false, null);
        assertNotNull(spr.getInstanceId());

        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());
        byte[] ab = getLog(pir.getLogFileName());

        assertLog(".*Hello!.*", ab);
    }
}
