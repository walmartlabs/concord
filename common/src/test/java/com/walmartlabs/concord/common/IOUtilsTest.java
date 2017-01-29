package com.walmartlabs.concord.common;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class IOUtilsTest {

    @Test
    public void testCopy() throws Exception {
        Path src = Files.createTempDirectory("test");
        Path dst = Files.createTempDirectory("test");

        // ---

        Path nestedDir = src.resolve("a/b");
        Files.createDirectories(nestedDir);

        Path srcFile = nestedDir.resolve("c.txt");
        Files.createFile(srcFile);

        // ---

        IOUtils.copy(src, dst);
        assertTrue(Files.exists(dst.resolve("a/b/c.txt")));
    }
}
