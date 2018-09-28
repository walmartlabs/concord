package com.walmartlabs.concord.server.process.state;

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

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.common.Posix;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

public interface ProcessStateManager {

    String PATH_SEPARATOR = "/";
    long MAX_IMPORT_BATCH_SIZE_BYTES = 64 * 1024 * 1024;

    /**
     * Fetches a single value specified by its path and applies a converter function.
     */
    <T> Optional<T> get(UUID instanceId, String path, Function<InputStream, Optional<T>> converter);

    /**
     * Fetches multiple values whose path begins with the specified value and applies a converter function
     * to each value.
     */
    <T> List<T> forEach(UUID instanceId, String path, Function<InputStream, Optional<T>> converter);

    /**
     * Retrieves a list of resources whose path begins with the specified value.
     */
    List<String> list(UUID instanceId, String path);

    /**
     * Finds all item paths that starts with the specified value.
     */
    <T> Optional<T> findPath(UUID instanceId, String path, Function<Stream<String>, Optional<T>> converter);

    /**
     * Checks if a value exists.
     */
    boolean exists(UUID instanceId, String path);

    /**
     * Removes single value.
     */
    void deleteFile(UUID instanceId, String path);

    /**
     * Remove a directory and all content from it.
     */
    void deleteDirectory(UUID instanceId, String path);

    /**
     * Removes all process data.
     */
    void delete(UUID instanceId);

    /**
     * Replaces a single value.
     */
    void replace(UUID instanceId, String path, byte[] data);

    /**
     * Inserts a single value.
     */
    void insert(UUID instanceId, String path, byte[] data);

    /**
     * Imports data from the specified directory or a file.
     *
     * @param instanceId process instance ID
     * @param path       target path prefix
     * @param src        source directory or a file
     */
    void importPath(UUID instanceId, String path, Path src);

    /**
     * Imports data from the specified directory or a file replacing the existing data.
     * If the filter function returns {@code false}, the matching file will be skipped.
     *
     * @param instanceId process instance ID
     * @param src        source directory or a file
     * @param filter     filter function
     */
    void replacePath(UUID instanceId, Path src, Function<Path, Boolean> filter);

    /**
     * Exports all data of a process instance.
     *
     * @param instanceId process instance ID
     * @param consumer   a function that receives the name of a file and a data stream
     * @return {@code true} if at least a single element was exported.
     */
    boolean export(UUID instanceId, ItemConsumer consumer);

    /**
     * Exports elements whose path begins with the specified value.
     *
     * @param instanceId process instance ID
     * @param path       path prefix
     * @param consumer   a function that receives the name of a file and a data stream
     * @return {@code true} if at least a single element was exported.
     */
    boolean exportDirectory(UUID instanceId, String path, ItemConsumer consumer);

    /**
     * Copies the data to the specified target directory.
     *
     * @param dst     target directory
     * @param options optional copy options
     */
    static ItemConsumer copyTo(Path dst, OpenOption... options) {
        return new CopyConsumer(dst, null, options);
    }

    /**
     * Copies the data to the specified target directory. Skips the ignored files.
     *
     * @param dst     target directory
     * @param ignored name patterns or ignored files
     * @param options optional copy options
     */
    static ItemConsumer copyTo(Path dst, String[] ignored, OpenOption... options) {
        return new CopyConsumer(dst, ignored, options);
    }

    /**
     * Puts all elements into the specified ZIP archive stream.
     *
     * @param dst archive stream.
     */
    static ItemConsumer zipTo(ZipArchiveOutputStream dst) {
        return new ZipConsumer(dst);
    }

    /**
     * Creates a path from the specified array of elements.
     *
     * @param elements
     */
    static String path(String... elements) {
        return String.join(PATH_SEPARATOR, elements);
    }

    interface ItemConsumer {

        void accept(String name, int unixMode, InputStream src);
    }

    final class CopyConsumer implements ItemConsumer {

        private final Path dst;
        private final String[] ignored;
        private final OpenOption[] options;

        private CopyConsumer(Path dst, String[] ignored, OpenOption[] options) { // NOSONAR
            this.dst = dst;
            this.ignored = ignored;
            this.options = options;
        }

        @Override
        public void accept(String name, int unixMode, InputStream src) {
            if (ignored != null) {
                for (String i : ignored) {
                    if (name.matches(i)) {
                        return;
                    }
                }
            }

            Path p = dst.resolve(name);

            try {
                Path parent = p.getParent();
                if (parent != null && !Files.exists(parent)) {
                    Files.createDirectories(parent);
                }

                try (OutputStream dst = Files.newOutputStream(p, options)) {
                    IOUtils.copy(src, dst);
                }

                Files.setPosixFilePermissions(p, Posix.posix(unixMode));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    final class ZipConsumer implements ItemConsumer {

        private final ZipArchiveOutputStream dst;

        private ZipConsumer(ZipArchiveOutputStream dst) {
            this.dst = dst;
        }

        @Override
        public void accept(String name, int unixMode, InputStream src) {
            ZipArchiveEntry entry = new ZipArchiveEntry(name);
            entry.setUnixMode(unixMode);

            try {
                dst.putArchiveEntry(entry);
                IOUtils.copy(src, dst);
                dst.closeArchiveEntry();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
