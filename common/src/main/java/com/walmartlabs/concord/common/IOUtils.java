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

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

public final class IOUtils {

    private static final Logger log = LoggerFactory.getLogger(IOUtils.class);

    public static final String TMP_DIR_KEY = "CONCORD_TMP_DIR";
    public static final Path TMP_DIR;

    private static final int MAX_COPY_DEPTH = 100;

    static {
        String s = getEnv(TMP_DIR_KEY, null);
        if (s == null) {
            // enforce alternative temp dir location for "security" reasons
            throw new IllegalArgumentException("Environment variable '" + TMP_DIR_KEY + "' must be set");
        }

        TMP_DIR = Paths.get(s);

        try {
            if (!Files.exists(TMP_DIR)) {
                Files.createDirectories(TMP_DIR);
            }
            log.info("Using {} as CONCORD_TMP_DIR", TMP_DIR);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static TemporaryPath tempFile(String prefix, String suffix) throws IOException {
        return new TemporaryPath(IOUtils.createTempFile(prefix, suffix));
    }

    public static Path createTempFile(String prefix, String suffix) throws IOException {
        return Files.createTempFile(TMP_DIR, prefix, suffix);
    }

    public static Path createTempDir(Path dir, String prefix) throws IOException {
        return Files.createTempDirectory(dir, prefix);
    }

    public static TemporaryPath tempDir(String prefix) throws IOException {
        return new TemporaryPath(IOUtils.createTempDir(prefix));
    }

    public static Path createTempDir(String prefix) throws IOException {
        return Files.createTempDirectory(TMP_DIR, prefix);
    }

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
        try (TemporaryPath tmpZip = new TemporaryPath(IOUtils.createTempFile("unzip", "zip"))) {
            Files.copy(in, tmpZip.path(), StandardCopyOption.REPLACE_EXISTING);
            IOUtils.unzip(tmpZip.path(), targetDir, options);
        }
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
        _copy(1, src, src, dst, options);
    }

    private static void _copy(int depth, Path root, Path src, Path dst, CopyOption... options) throws IOException {
        if (depth >= MAX_COPY_DEPTH) {
            throw new IOException("Too deep: " + src);
        }

        Files.walkFileTree(src, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path a = file;
                Path b = dst.resolve(src.relativize(file));

                if (Files.isSymbolicLink(file)) {
                    Path link = Files.readSymbolicLink(file);
                    a = file.getParent().resolve(link).normalize();

                    if (!a.startsWith(root)) {
                        throw new IOException("External symlinks are not supported: " + file + " -> " + a);
                    }

                    if (Files.notExists(a)) {
                        // missing target
                        return FileVisitResult.CONTINUE;
                    }

                    if (Files.isDirectory(a)) {
                        _copy(depth + 1, root, a, b);
                        return FileVisitResult.CONTINUE;
                    }
                }

                Path parent = b.getParent();
                if (!Files.exists(parent)) {
                    Files.createDirectories(parent);
                }

                Files.copy(a, b, options);
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

    public static byte[] toByteArray(InputStream src) throws IOException {
        ByteArrayOutputStream dst = new ByteArrayOutputStream();
        copy(src, dst);
        return dst.toByteArray();
    }

    private static String getEnv(String key, String defaultValue) {
        String s = System.getenv(key);
        if (s == null) {
            return defaultValue;
        }
        return s;
    }

    private IOUtils() {
    }
}
