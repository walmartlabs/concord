package com.walmartlabs.concord.common;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ZipUtilsTest {

    @Test
    public void testZipUnzip() throws Exception {
        Path src = Files.createTempDirectory("test-zip");
        Files.createFile(src.resolve("a.txt"));
        Files.createFile(src.resolve("b\\c.txt"));
        Files.createDirectory(src.resolve("b"));
        Files.createFile(src.resolve("b").resolve("c.txt"));

        Path archive = Files.createTempFile("archive", "zip");

        try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(Files.newOutputStream(archive))) {
            ZipUtils.zip(zip, src);
        }

        PathUtils.deleteRecursively(src);

        Path dst = Files.createTempDirectory("test");
        ZipUtils.unzip(archive, dst);
        assertTrue(Files.exists(dst.resolve("a.txt")));
        assertTrue(Files.exists(dst.resolve("b\\c.txt")));
        assertTrue(Files.exists(dst.resolve("b").resolve("c.txt")));
    }
}
