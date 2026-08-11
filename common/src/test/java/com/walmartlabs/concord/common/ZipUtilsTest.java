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

import com.walmartlabs.concord.common.cfg.UnzipLimits;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipUtilsTest {

    @TempDir
    Path workDir;

    @Test
    void testZipUnzip() throws Exception {
        Path src = Files.createTempDirectory(workDir, "test-zip");
        Files.createFile(src.resolve("a.txt"));
        Files.createFile(src.resolve("b\\c.txt"));
        Files.createDirectory(src.resolve("b"));
        Files.createFile(src.resolve("b").resolve("c.txt"));

        Path archive = Files.createTempFile("archive", "zip");

        try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(Files.newOutputStream(archive))) {
            ZipUtils.zip(zip, src);
        }

        PathUtils.deleteRecursively(src);

        Path dst = Files.createTempDirectory(workDir, "test");
        ZipUtils.unzip(archive, dst);
        assertTrue(Files.exists(dst.resolve("a.txt")));
        assertTrue(Files.exists(dst.resolve("b\\c.txt")));
        assertTrue(Files.exists(dst.resolve("b").resolve("c.txt")));
    }

    @Test
    void testUnzipRejectsLargeEntry() throws Exception {
        Path archive = Files.createTempFile(workDir, "archive-large-entry", ".zip");

        try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(Files.newOutputStream(archive))) {
            zip.putArchiveEntry(new org.apache.commons.compress.archivers.zip.ZipArchiveEntry("large.txt"));
            zip.write(new byte[64]);
            zip.closeArchiveEntry();
        }

        Path dst = Files.createTempDirectory(workDir, "test-large-entry");

        UnzipLimits limits = new UnzipLimits(10, 1024, 16, 100);
        assertThrows(IOException.class, () -> ZipUtils.unzip(archive, dst, false, null, limits));
        assertFalse(Files.exists(dst.resolve("large.txt")));
    }

    @Test
    void testUnzipRejectsTooManyEntries() throws Exception {
        Path archive = Files.createTempFile(workDir, "archive-many-entries", ".zip");

        try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(Files.newOutputStream(archive))) {
            for (int i = 0; i < 3; i++) {
                zip.putArchiveEntry(new org.apache.commons.compress.archivers.zip.ZipArchiveEntry("f" + i + ".txt"));
                zip.write(new byte[]{1});
                zip.closeArchiveEntry();
            }
        }

        Path dst = Files.createTempDirectory(workDir, "test-many-entries");

        UnzipLimits limits = new UnzipLimits(2, 1024, 64, 100);
        assertThrows(IOException.class, () -> ZipUtils.unzip(archive, dst, false, null, limits));
    }
}
