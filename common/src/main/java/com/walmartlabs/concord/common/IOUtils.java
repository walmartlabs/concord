package com.walmartlabs.concord.common;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

public final class IOUtils {

    public static boolean matches(Path p, String... filters) {
        String n = p.getName(p.getNameCount() - 1).toString();
        for (String f : filters) {
            if (n.matches(f)) {
                return true;
            }
        }
        return false;
    }

    public static void zipFile(ZipArchiveOutputStream zip, Path src, String name) throws IOException {
        ZipArchiveEntry e = new ZipArchiveEntry(name);

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
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
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

    public static void unzip(Path in, Path targetDir, CopyOption... options) throws IOException {
        unzip(in, targetDir, false, options);
    }

    public static void unzip(Path in, Path targetDir, boolean skipExisting, CopyOption... options) throws IOException {
        try (ZipFile zip = new ZipFile(in.toFile())) {
            Enumeration<ZipArchiveEntry> entries = zip.getEntries();

            while (entries.hasMoreElements()) {
                ZipArchiveEntry e = entries.nextElement();

                Path p = targetDir.resolve(e.getName());
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
                }
            }
        }
    }

    public static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] ab = new byte[4096];
        int read;
        while ((read = in.read(ab)) > 0) {
            out.write(ab, 0, read);
        }
    }

    public static void copy(Path src, Path dst) throws IOException {
        copy(src, dst, new CopyOption[0]);
    }

    public static void copy(Path src, Path dst, CopyOption... options) throws IOException {
        Files.walkFileTree(src, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path p = dst.resolve(src.relativize(file));

                Path pp = p.getParent();
                if (!Files.exists(pp)) {
                    Files.createDirectories(pp);
                }

                Files.copy(file, p, options);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static List<String> grep(String pattern, byte[] ab) throws IOException {
        return grep(pattern, new ByteArrayInputStream(ab));
    }

    public static List<String> grep(String pattern, InputStream in) throws IOException {
        List<String> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.matches(pattern)) {
                    result.add(line);
                }
            }
        }
        return result;
    }

    public static void closeQuietly(Closeable c) {
        try {
            c.close();
        } catch (IOException e) {
            // ignore
        }
    }

    public static boolean deleteRecursively(Path p) throws IOException {
        if (!Files.exists(p)) {
            return false;
        }

        if (!Files.isDirectory(p)) {
            Files.delete(p);
            return true;
        }

        Files.walkFileTree(p, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });

        return true;
    }

    private IOUtils() {
    }
}
