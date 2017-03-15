package com.walmartlabs.concord.server.process;

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.cfg.AttachmentStoreConfiguration;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

public class ProcessAttachmentManagerTest {

    @Test
    public void test() throws Exception {
        Path tmpDir = Files.createTempDirectory("test");
        ProcessAttachmentManager m = new ProcessAttachmentManager(new AttachmentStoreConfiguration(tmpDir));

        // ---

        URI files = ProcessAttachmentManagerTest.class.getResource("attachmentTest").toURI();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(baos)) {
            IOUtils.zip(zip, Paths.get(files));
        }

        // ---

        String instanceId = UUID.randomUUID().toString();
        m.store(instanceId, new ByteArrayInputStream(baos.toByteArray()));

        // ---

        Path t0 = m.extract(instanceId, "test0.txt");
        assertNotNull(t0);
        assertEquals("0", new String(Files.readAllBytes(t0)));

        // ---

        Path dir1 = m.extract(instanceId, "dir1/");
        assertNotNull(dir1);
        assertTrue(Files.exists(dir1.resolve("test1.txt")));

        // ---

        Path dir2 = m.extract(instanceId, "/dir2/");
        assertNotNull(dir2);
        assertTrue(Files.exists(dir2.resolve("test2.txt")));

        // ---

        Path tNa = m.extract(instanceId, "somethingElse.txt");
        assertNull(tNa);
    }
}
