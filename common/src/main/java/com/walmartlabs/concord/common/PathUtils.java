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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.NotNull;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.List;

public final class PathUtils {

    public static final String TMP_DIR_KEY = "CONCORD_TMP_DIR";
    public static final Path TMP_DIR = Paths.get(getEnv(TMP_DIR_KEY, System.getProperty("java.io.tmpdir")));

    private static final Logger log = LoggerFactory.getLogger(PathUtils.class);

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
        return new TemporaryPath(createTempFile(prefix, suffix));
    }

    public static Path createTempFile(String prefix, String suffix) throws IOException {
        return Files.createTempFile(TMP_DIR, prefix, suffix);
    }

    public static Path createTempDir(Path dir, String prefix) throws IOException {
        return Files.createTempDirectory(dir, prefix);
    }

    public static TemporaryPath tempDir(String prefix) throws IOException {
        return new TemporaryPath(createTempDir(prefix));
    }

    public static Path createTempDir(String prefix) throws IOException {
        return Files.createTempDirectory(TMP_DIR, prefix);
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

    public static void delete(File f) {
        if (f == null || !f.exists()) {
            return;
        }

        if (!f.delete()) {
            log.warn("delete ['{}'] -> failed", f.getAbsolutePath());
        }
    }

    /**
     * Resolves a child path within a parent, asserting the normalized child
     * starts with the parent path to avoid relative path escaping (e.g.
     * {@code "../../not/in/parent"}).
     * @param parent parent path within which child must exist when resolved
     * @param child filename or path to resolve as a child of {@code parent}
     * @return normalized child path
     * @throws IOException when the child does not resolve to an absolute path within the parent path
     */
    public static Path assertInPath(@NotNull Path parent, @NotNull String child) throws IOException {
        Path normalizedParent = parent.normalize().toAbsolutePath();
        Path normalizedChild = normalizedParent.resolve(child).normalize().toAbsolutePath();

        if (!normalizedChild.startsWith(normalizedParent)) {
            throw new IOException("Child path resolves outside of parent path: " + child);
        }

        return normalizedChild;
    }

    private static String getEnv(String key, String defaultValue) {
        String s = System.getenv(key);
        if (s == null) {
            return defaultValue;
        }
        return s;
    }

    private PathUtils() {
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
}
