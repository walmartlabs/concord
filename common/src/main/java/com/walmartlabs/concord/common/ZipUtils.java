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

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Enumeration;
import java.util.Set;

public class ZipUtils {

    public static void zipFile(ZipArchiveOutputStream zip, Path src, String name) throws IOException {
        ZipArchiveEntry e = new ZipArchiveEntry(name) {
            @Override
            public int getPlatform() {
                return PLATFORM_UNIX;
            }
        };

        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(src);
        e.setUnixMode(Posix.unixMode(permissions));

        e.setSize(Files.size(src));

        zip.putArchiveEntry(e);
        Files.copy(src, zip);
        zip.closeArchiveEntry();
    }

    public static void zip(ZipArchiveOutputStream zip, Path srcDir, String... filters) throws IOException {
        zip(zip, null, srcDir, filters);
    }

    public static void zip(ZipArchiveOutputStream zip, String dstPrefix, Path srcDir, String... filters) throws IOException {
        Files.walkFileTree(srcDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (dir.toAbsolutePath().equals(srcDir)) {
                    return FileVisitResult.CONTINUE;
                }

                if (matches(dir, filters)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (matches(file, filters)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }

                String n = srcDir.relativize(file).toString();
                if (dstPrefix != null) {
                    n = dstPrefix + n;
                }

                zipFile(zip, file, n);

                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void unzip(InputStream in, Path targetDir, CopyOption... options) throws IOException {
        try (TemporaryPath tmpZip = new TemporaryPath(PathUtils.createTempFile("unzip", "zip"))) {
            Files.copy(in, tmpZip.path(), StandardCopyOption.REPLACE_EXISTING);
            unzip(tmpZip.path(), targetDir, options);
        }
    }

    public static void unzip(Path in, Path targetDir, CopyOption... options) throws IOException {
        unzip(in, targetDir, false, null, options);
    }

    public static void unzip(Path in, Path targetDir, boolean skipExisting, CopyOption... options) throws IOException {
        unzip(in, targetDir, skipExisting, null, options);
    }

    public static void unzip(InputStream in, Path targetDir, boolean skipExisting, FileVisitor visitor, CopyOption... options) throws IOException {
        try (TemporaryPath tmpZip = new TemporaryPath(PathUtils.createTempFile("unzip", "zip"))) {
            Files.copy(in, tmpZip.path(), StandardCopyOption.REPLACE_EXISTING);
            unzip(tmpZip.path(), targetDir, skipExisting, visitor, options);
        }
    }

    public static void unzip(Path in, Path targetDir, boolean skipExisting, FileVisitor visitor, CopyOption... options) throws IOException {
        targetDir = targetDir.normalize().toAbsolutePath();

        try (ZipFile zip = new ZipFile(in.toFile())) {
            Enumeration<ZipArchiveEntry> entries = zip.getEntries();

            while (entries.hasMoreElements()) {
                ZipArchiveEntry e = entries.nextElement();

                Path p = targetDir.resolve(e.getName());

                // skip paths outside of targetDir
                // (don't log anything to avoid "log bombing")
                if (!p.normalize().toAbsolutePath().startsWith(targetDir)) {
                    continue;
                }

                if (skipExisting && Files.exists(p)) {
                    continue;
                }

                if (e.isDirectory()) {
                    Files.createDirectories(p);
                } else {
                    Path parent = p.getParent();
                    if (!Files.exists(parent)) {
                        Files.createDirectories(parent);
                    }

                    try (InputStream src = zip.getInputStream(e)) {
                        Files.copy(src, p, options);
                    }

                    int unixMode = e.getUnixMode();
                    if (unixMode <= 0) {
                        unixMode = Posix.DEFAULT_UNIX_MODE;
                    }

                    Files.setPosixFilePermissions(p, Posix.posix(unixMode));
                    if (visitor != null) {
                        visitor.visit(p, p);
                    }
                }
            }
        }
    }

    private static boolean matches(Path p, String... filters) {
        String n = p.getName(p.getNameCount() - 1).toString();
        for (String f : filters) {
            if (n.matches(f)) {
                return true;
            }
        }
        return false;
    }
}
