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
import java.util.*;

public final class IOUtils {

    private static final Logger log = LoggerFactory.getLogger(IOUtils.class);

    public static final String TMP_DIR_KEY = "CONCORD_TMP_DIR";
    public static final Path TMP_DIR = Paths.get(getEnv(TMP_DIR_KEY, System.getProperty("java.io.tmpdir")));

    static {
        try {
            if (!Files.exists(TMP_DIR)) {
                Files.createDirectories(TMP_DIR);
            }
            log.debug("Using {} as CONCORD_TMP_DIR", TMP_DIR);
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
        try (TemporaryPath tmpZip = new TemporaryPath(IOUtils.createTempFile("unzip", "zip"))) {
            Files.copy(in, tmpZip.path(), StandardCopyOption.REPLACE_EXISTING);
            IOUtils.unzip(tmpZip.path(), targetDir, options);
        }
    }

    public static void unzip(Path in, Path targetDir, CopyOption... options) throws IOException {
        unzip(in, targetDir, false, null, options);
    }

    public static void unzip(Path in, Path targetDir, boolean skipExisting, CopyOption... options) throws IOException {
        unzip(in, targetDir, skipExisting, null, options);
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

    public static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] ab = new byte[4096];
        int read;
        while ((read = in.read(ab)) > 0) {
            out.write(ab, 0, read);
        }
    }

    public static void copy(Path src, Path dst) throws IOException {
        copy(src, dst, (String) null, null, new CopyOption[0]);
    }

    public static void copy(Path src, Path dst, CopyOption... options) throws IOException {
        copy(src, dst, (String) null, null, options);
    }

    public static void copy(Path src, Path dst, String ignorePattern, CopyOption... options) throws IOException {
        _copy(src, src, dst, toList(ignorePattern), null, options);
    }

    public static void copy(Path src, Path dst, String skipContents, FileVisitor visitor, CopyOption... options) throws IOException {
        _copy(src, src, dst, toList(skipContents), visitor, options);
    }

    public static void copy(Path src, Path dst, List<String> skipContents, FileVisitor visitor, CopyOption... options) throws IOException {
        _copy(src, src, dst, skipContents, visitor, options);
    }

    private static void _copy(Path root, Path src, Path dst, List<String> ignorePattern, FileVisitor visitor, CopyOption... options) throws IOException {
        Files.walkFileTree(src, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (dir != src && anyMatch(src.relativize(dir).toString(), ignorePattern)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file != src && anyMatch(src.relativize(file).toString(), ignorePattern)) {
                    return FileVisitResult.CONTINUE;
                }

                Path a = file;
                Path b = dst.resolve(src.relativize(file));

                Path parent = b.getParent();
                if (!Files.exists(parent)) {
                    Files.createDirectories(parent);
                }

                if (Files.isSymbolicLink(file)) {
                    Path link = Files.readSymbolicLink(file);
                    Path target = file.getParent().resolve(link).normalize();

                    if (!target.startsWith(root)) {
                        throw new IOException("Symlinks outside the base directory are not supported: " + file + " -> " + target);
                    }

                    if (Files.notExists(target)) {
                        // missing target
                        return FileVisitResult.CONTINUE;
                    }

                    Files.createSymbolicLink(b, link);
                    return FileVisitResult.CONTINUE;
                }

                Files.copy(a, b, options);

                if (visitor != null) {
                    visitor.visit(a, b);
                }

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

    public static void delete(File f) {
        if (f == null || !f.exists()) {
            return;
        }

        if (!f.delete()) {
            log.warn("delete ['{}'] -> failed", f.getAbsolutePath());
        }
    }

    private static String getEnv(String key, String defaultValue) {
        String s = System.getenv(key);
        if (s == null) {
            return defaultValue;
        }
        return s;
    }

    private static List<String> toList(String entry) {
        if (entry == null) {
            return Collections.emptyList();
        }

        return Collections.singletonList(entry);
    }

    private static boolean anyMatch(String what, List<String> patterns) {
        if (patterns == null) {
            return false;
        }

        return patterns.stream().anyMatch(what::matches);
    }

    private IOUtils() {
    }
}
