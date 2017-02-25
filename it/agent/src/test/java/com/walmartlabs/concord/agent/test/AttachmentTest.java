package com.walmartlabs.concord.agent.test;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AttachmentTest {

    public static void main(String[] args) throws Exception {
        String s = System.getenv("_CONCORD_ATTACHMENTS_DIR");

        Path dir = Paths.get(s);
        Files.createDirectories(dir);

        Path f = dir.resolve("test.txt");
        try (OutputStream out = Files.newOutputStream(f)) {
            out.write("hi".getBytes());
        }
    }
}
