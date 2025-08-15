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

import javax.validation.constraints.NotNull;
import java.io.*;
import java.nio.file.CopyOption;
import java.nio.file.Path;
import java.util.List;

/**
 * @deprecated use the alternatives in {@link PathUtils}, {@link ZipUtils}, {@link GrepUtils}, etc.
 */
@Deprecated
public final class IOUtils {

    /**
     * @deprecated use {@link PathUtils#TMP_DIR_KEY}
     */
    @Deprecated
    public static final String TMP_DIR_KEY = PathUtils.TMP_DIR_KEY;

    /**
     * @deprecated use {@link PathUtils#TMP_DIR}
     */
    @Deprecated
    public static final Path TMP_DIR = PathUtils.TMP_DIR;

    /**
     * @deprecated use {@link PathUtils#tempFile(String, String)}
     */
    @Deprecated
    public static TemporaryPath tempFile(String prefix, String suffix) throws IOException {
        return PathUtils.tempFile(prefix, suffix);
    }

    /**
     * @deprecated use {@link PathUtils#createTempFile(String, String)}
     */
    @Deprecated
    public static Path createTempFile(String prefix, String suffix) throws IOException {
        return PathUtils.createTempFile(prefix, suffix);
    }

    /**
     * @deprecated use {@link PathUtils#createTempDir(Path, String)}
     */
    @Deprecated
    public static Path createTempDir(Path dir, String prefix) throws IOException {
        return PathUtils.createTempDir(dir, prefix);
    }

    /**
     * @deprecated use {@link PathUtils#tempDir(String)}
     */
    @Deprecated
    public static TemporaryPath tempDir(String prefix) throws IOException {
        return PathUtils.tempDir(prefix);
    }

    /**
     * @deprecated use {@link PathUtils#createTempDir(String)}
     */
    @Deprecated
    public static Path createTempDir(String prefix) throws IOException {
        return PathUtils.createTempDir(prefix);
    }

    /**
     * @deprecated use {@link ZipUtils#zipFile(ZipArchiveOutputStream, Path, String)}
     */
    @Deprecated
    public static void zipFile(ZipArchiveOutputStream zip, Path src, String name) throws IOException {
        ZipUtils.zipFile(zip, src, name);
    }

    /**
     * @deprecated use {@link ZipUtils#zip(ZipArchiveOutputStream, Path, String...)}
     */
    @Deprecated
    public static void zip(ZipArchiveOutputStream zip, Path srcDir, String... filters) throws IOException {
        ZipUtils.zip(zip, srcDir, filters);
    }

    /**
     * @deprecated use {@link ZipUtils#zip(ZipArchiveOutputStream, String, Path, String...)}
     */
    @Deprecated
    public static void zip(ZipArchiveOutputStream zip, String dstPrefix, Path srcDir, String... filters) throws IOException {
        ZipUtils.zip(zip, dstPrefix, srcDir, filters);
    }

    /**
     * @deprecated use {@link ZipUtils#unzip(InputStream, Path, CopyOption...)}
     */
    @Deprecated
    public static void unzip(InputStream in, Path targetDir, CopyOption... options) throws IOException {
        ZipUtils.unzip(in, targetDir, options);
    }

    /**
     * @deprecated use {@link ZipUtils#unzip(Path, Path, CopyOption...)}
     */
    @Deprecated
    public static void unzip(Path in, Path targetDir, CopyOption... options) throws IOException {
        ZipUtils.unzip(in, targetDir, options);
    }

    /**
     * @deprecated use {@link ZipUtils#unzip(Path, Path, boolean, CopyOption...)}
     */
    @Deprecated
    public static void unzip(Path in, Path targetDir, boolean skipExisting, CopyOption... options) throws IOException {
        ZipUtils.unzip(in, targetDir, skipExisting, options);
    }

    /**
     * @deprecated use {@link ZipUtils#unzip(InputStream, Path, boolean, FileVisitor, CopyOption...)}
     */
    @Deprecated
    public static void unzip(InputStream in, Path targetDir, boolean skipExisting, FileVisitor visitor, CopyOption... options) throws IOException {
        ZipUtils.unzip(in, targetDir, skipExisting, visitor, options);
    }

    /**
     * @deprecated use {@link ZipUtils#unzip(Path, Path, boolean, FileVisitor, CopyOption...)}
     */
    @Deprecated
    public static void unzip(Path in, Path targetDir, boolean skipExisting, FileVisitor visitor, CopyOption... options) throws IOException {
        ZipUtils.unzip(in, targetDir, skipExisting, visitor, options);
    }

    /**
     * @deprecated use {@link InputStream#transferTo(OutputStream)}
     */
    @Deprecated
    public static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] ab = new byte[4096];
        int read;
        while ((read = in.read(ab)) > 0) {
            out.write(ab, 0, read);
        }
    }

    /**
     * @deprecated use {@link PathUtils#copy(Path, Path)}
     */
    @Deprecated
    public static void copy(Path src, Path dst) throws IOException {
        PathUtils.copy(src, dst);
    }

    /**
     * @deprecated use {@link PathUtils#copy(Path, Path, CopyOption...)}
     */
    @Deprecated
    public static void copy(Path src, Path dst, CopyOption... options) throws IOException {
        PathUtils.copy(src, dst, options);
    }

    /**
     * @deprecated use {@link PathUtils#copy(Path, Path, String, CopyOption...)}
     */
    @Deprecated
    public static void copy(Path src, Path dst, String ignorePattern, CopyOption... options) throws IOException {
        PathUtils.copy(src, dst, ignorePattern, options);
    }

    /**
     * @deprecated use {@link PathUtils#copy(Path, Path, String, FileVisitor, CopyOption...)}
     */
    @Deprecated
    public static void copy(Path src, Path dst, String skipContents, FileVisitor visitor, CopyOption... options) throws IOException {
        PathUtils.copy(src, dst, skipContents, visitor, options);
    }

    /**
     * @deprecated use {@link PathUtils#copy(Path, Path, List, FileVisitor, CopyOption...)}
     */
    @Deprecated
    public static void copy(Path src, Path dst, List<String> skipContents, FileVisitor visitor, CopyOption... options) throws IOException {
        PathUtils.copy(src, dst, skipContents, visitor, options);
    }

    /**
     * @deprecated use {@link GrepUtils#grep(String, byte[])}
     */
    @Deprecated
    public static List<String> grep(String pattern, byte[] ab) throws IOException {
        return GrepUtils.grep(pattern, ab);
    }

    /**
     * @deprecated use {@link GrepUtils#grep(String, InputStream)}
     */
    @Deprecated
    public static List<String> grep(String pattern, InputStream in) throws IOException {
        return GrepUtils.grep(pattern, in);
    }

    /**
     * @deprecated use {@link PathUtils#deleteRecursively(Path)}
     */
    @Deprecated
    public static boolean deleteRecursively(Path p) throws IOException {
        return PathUtils.deleteRecursively(p);
    }

    /**
     * @deprecated use {@link InputStream#readAllBytes()}
     */
    @Deprecated
    public static byte[] toByteArray(InputStream src) throws IOException {
        ByteArrayOutputStream dst = new ByteArrayOutputStream();
        copy(src, dst);
        return dst.toByteArray();
    }

    /**
     * @deprecated use {@link PathUtils#delete(File)}
     */
    @Deprecated
    public static void delete(File f) {
        PathUtils.delete(f);
    }

    /**
     * @deprecated use {@link PathUtils#assertInPath(Path, String)}
     */
    @Deprecated
    public static Path assertInPath(@NotNull Path parent, @NotNull String child) throws IOException {
        return PathUtils.assertInPath(parent, child);
    }

    private IOUtils() {
    }
}
