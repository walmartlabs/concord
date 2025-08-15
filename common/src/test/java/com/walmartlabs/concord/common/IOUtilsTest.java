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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
