package com.walmartlabs.concord.server;

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.cfg.LogStoreConfiguration;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class LogManagerTest {

    private static LogManager logManager;

    @BeforeClass
    public static void init() throws Exception {
        logManager = new LogManager(new LogStoreConfiguration());
    }

    @Test
    public void testSimpleString() throws Exception {
        String id = UUID.randomUUID().toString();

        logManager.error(id, "Error while unpacking an archive");
        assertLog(id, ".*Error while unpacking an archive.*");
    }

    @Test
    public void testWithStringConcat() throws Exception {
        String id = UUID.randomUUID().toString();

        String s = "archive";
        logManager.error(id, "Error while unpacking an archive: " + s);
        assertLog(id, ".*Error while unpacking an archive: archive.*");
    }

    @Test
    public void testWithStringConcatWithException() throws Exception {
        String id = UUID.randomUUID().toString();

        String s = "archive";
        logManager.error(id, "Error while unpacking an archive: " + s, new Exception("test-exception"));
        assertLog(id, ".*Error while unpacking an archive: archive.*", ".*test-exception.*");
    }

    @Test
    public void testWithPlaceholders() throws Exception {
        String id = UUID.randomUUID().toString();

        String s = "string";
        logManager.warn(id, "Message with placeholders: {}, {}, {}", s, true, 78945361);
        assertLog(id, ".*Message with placeholders: string, true, 78945361.*");
    }

    @Test
    public void testWithPlaceholdersWithException() throws Exception {
        String id = UUID.randomUUID().toString();

        String s = "string";
        logManager.warn(id, "Message with placeholders: {}, {}, {}", s, true, 78945361, new Exception("test-exception"));
        assertLog(id, ".*Message with placeholders: string, true, 78945361.*", ".*test-exception.*");
    }

    private void assertLog(String id, String pattern) throws IOException {
        try(InputStream is = Files.newInputStream(logManager.getPath(id))) {
            assertEquals(1, IOUtils.grep(pattern, is).size());
        }
    }

    private void assertLog(String id, String pattern1, String pattern2) throws IOException {
        assertLog(id, pattern1);
        assertLog(id, pattern2);
    }
}
