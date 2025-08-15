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
}
