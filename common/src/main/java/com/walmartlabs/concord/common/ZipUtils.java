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
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Enumeration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public final class ZipUtils {

    private static final UnzipLimits DEFAULT_UNZIP_LIMITS = new UnzipLimits(
            10_000,
            1024L * 1024L * 1024L,
            256L * 1024L * 1024L,
            200
    );

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
        Files.walkFileTree(srcDir, new SimpleFileVisitor<>() {
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
        unzip(in, targetDir, false, null, DEFAULT_UNZIP_LIMITS, options);
    }

    public static void unzip(Path in, Path targetDir, boolean skipExisting, CopyOption... options) throws IOException {
        unzip(in, targetDir, skipExisting, null, DEFAULT_UNZIP_LIMITS, options);
    }

    public static void unzip(InputStream in, Path targetDir, boolean skipExisting, FileVisitor visitor, CopyOption... options) throws IOException {
        try (TemporaryPath tmpZip = new TemporaryPath(PathUtils.createTempFile("unzip", "zip"))) {
            Files.copy(in, tmpZip.path(), StandardCopyOption.REPLACE_EXISTING);
            unzip(tmpZip.path(), targetDir, skipExisting, visitor, DEFAULT_UNZIP_LIMITS, options);
        }
    }

    public static void unzip(Path in, Path targetDir, boolean skipExisting, FileVisitor visitor, CopyOption... options) throws IOException {
        unzip(in, targetDir, skipExisting, visitor, DEFAULT_UNZIP_LIMITS, options);
    }

    public static void unzip(
            Path in,
            Path targetDir,
            boolean skipExisting,
            FileVisitor visitor,
            UnzipLimits limits,
            CopyOption... options
    ) throws IOException {

        targetDir = targetDir.normalize().toAbsolutePath();
        Objects.requireNonNull(limits);

        BytesTracker totalBytes = new BytesTracker(limits.maxTotalUncompressedBytes());
        long entryCount = 0;

        try (ZipFile zip = new ZipFile.Builder().setFile(in.toFile()).get()) {
            Enumeration<ZipArchiveEntry> entries = zip.getEntries();

            while (entries.hasMoreElements()) {
                ZipArchiveEntry e = entries.nextElement();
                entryCount++;
                if (entryCount > limits.maxEntries()) {
                    throw new IOException("Unzip aborted: entry count limit exceeded: " + limits.maxEntries());
                }

                long advertisedSize = e.getSize();
                if (advertisedSize > limits.maxEntryUncompressedBytes()) {
                    throw new IOException("Unzip aborted: entry size limit exceeded for '" + e.getName() + "'");
                }

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
                        copyWithLimits(src, p, e, totalBytes, limits, options);
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

    private static class BytesTracker {
        private final long maxRead;
        private long currentRead;

        public BytesTracker(long maxRead) {
            this.maxRead = maxRead;
            this.currentRead = 0;
        }

        public void increment(long read) throws IOException {
            long result = currentRead += read;
            if (result > maxRead) {
                throw new IOException("Unzip aborted: total uncompressed size limit exceeded: " + maxRead);
            }
        }
    }

    private static void copyWithLimits(
            InputStream src,
            Path dst,
            ZipArchiveEntry e,
            BytesTracker totalBytes,
            UnzipLimits limits,
            CopyOption... options
    ) throws IOException {

        try (OutputStream out = Files.newOutputStream(dst, toOpenOptions(options))) {
            byte[] buf = new byte[8192];
            long entryBytes = 0;
            long compressedSize = e.getCompressedSize();

            int read;
            while ((read = src.read(buf)) >= 0) {
                if (read == 0) {
                    continue;
                }

                entryBytes += read;
                if (entryBytes > limits.maxEntryUncompressedBytes()) {
                    throw new IOException("Unzip aborted: entry size limit exceeded for '" + e.getName() + "'");
                }

                if (compressedSize > 0 && entryBytes > compressedSize * limits.maxCompressionRatio()) {
                    throw new IOException("Unzip aborted: compression ratio limit exceeded for '" + e.getName() + "'");
                }

                totalBytes.increment(read);
                out.write(buf, 0, read);
            }
        } catch (IOException ex) {
            Files.deleteIfExists(dst);
            throw ex;
        }
    }

    private static OpenOption[] toOpenOptions(CopyOption... options) {
        boolean replaceExisting = false;
        for (CopyOption o : options) {
            if (o == StandardCopyOption.REPLACE_EXISTING) {
                replaceExisting = true;
                break;
            }
        }

        if (replaceExisting) {
            return new OpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE};
        }

        return new OpenOption[]{StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE};
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

    private ZipUtils() {
    }
}
