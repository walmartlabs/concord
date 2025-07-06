package com.walmartlabs.concord.common;

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

import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IOUtilsTest {

    @Test
    public void testZipUnzip() throws Exception {
        Path src = Files.createTempDirectory("test-zip");
        Files.createFile(src.resolve("a.txt"));
        Files.createFile(src.resolve("b\\c.txt"));
        Files.createDirectory(src.resolve("b"));
        Files.createFile(src.resolve("b").resolve("c.txt"));

        Path archive = Files.createTempFile("archive", "zip");

        try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(Files.newOutputStream(archive))) {
            IOUtils.zip(zip, src);
        }

        IOUtils.deleteRecursively(src);

        Path dst = Files.createTempDirectory("test");
        IOUtils.unzip(archive, dst);
        assertTrue(Files.exists(dst.resolve("a.txt")));
        assertTrue(Files.exists(dst.resolve("b\\c.txt")));
        assertTrue(Files.exists(dst.resolve("b").resolve("c.txt")));
    }

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

    @Test
    public void testSymlinks() throws Exception {
        Path src = Files.createTempDirectory("test");

        Path aFile = src.resolve("a");
        Files.write(aFile, "hello".getBytes(), StandardOpenOption.CREATE);

        Path xDir = src.resolve("x");
        Files.createDirectories(xDir);

        Path bLink = xDir.resolve("b");
        Files.createSymbolicLink(bLink, aFile);

        // ---

        Path dst = Files.createTempDirectory("test");

        // ---

        IOUtils.copy(src, dst);

        // ---

        assertTrue(Files.isSymbolicLink(dst.resolve("x").resolve("b")));
        assertTrue(Files.isRegularFile(dst.resolve("x").resolve("b")));
    }

    @Test
    public void testExternalSymlinks() throws Exception {
        Path src = Files.createTempDirectory("test");

        Path link = src.resolve("a");
        Path target = Paths.get("../../../etc/passwd");
        Files.createSymbolicLink(link, target);

        // ---

        Path dst = Files.createTempDirectory("test");

        // ---

        Exception e = assertThrows(IOException.class, () -> IOUtils.copy(src, dst));
        assertTrue(e.getMessage().contains("Symlinks outside the base directory are not supported"));
    }

    @Test
    public void testResolveNonChild() {
        Path parent = Paths.get("/parent/path");
        Exception e = assertThrows(IOException.class, () -> IOUtils.assertInPath(parent, "../child"));
        assertTrue(e.getMessage().contains("Child path resolves outside of parent path"));
    }

    @Test
    public void testResolveValidChild() {
        Path parent = Paths.get("/parent/path");
        assertDoesNotThrow(() -> IOUtils.assertInPath(parent, "child"));
        assertDoesNotThrow(() -> IOUtils.assertInPath(parent, "another/child"));

        Path p = assertDoesNotThrow(() -> IOUtils.assertInPath(parent, "odd/../but/valid"));
        assertEquals("/parent/path/but/valid", p.toString());
    }
}
