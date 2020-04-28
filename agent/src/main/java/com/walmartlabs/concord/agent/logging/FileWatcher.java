package com.walmartlabs.concord.agent.logging;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public final class FileWatcher<T> implements Closeable {

    public static <T> void watch(Path path, Supplier<Boolean> stopCondition, long maxDelay, FileNameParser<T> fileNameParser, FileListener<T> listener) throws IOException {
        try (FileWatcher<T> watcher = new FileWatcher<>(path, maxDelay, fileNameParser, listener)) {
            watcher.run(stopCondition);
        }
    }

    private static final Logger log = LoggerFactory.getLogger(FileWatcher.class);

    private static final int MAX_OPEN_FILES = 10;

    private final Path watchDir;
    private final long maxDelay;
    private final FileListener<T> listener;
    private final FileNameParser<T> fileNameParser;

    private final FileCache fileCache = new FileCache();
    private final Map<Path, FileEntry<T>> filePointers = new HashMap<>();
    private final Set<Path> ignoreFiles = new HashSet<>();

    private FileWatcher(Path watchDir, long maxDelay, FileNameParser<T> fileNameParser, FileListener<T> listener) {
        this.watchDir = watchDir;
        this.maxDelay = maxDelay;
        this.listener = listener;
        this.fileNameParser = fileNameParser;
    }

    @Override
    public void close() {
        fileCache.close();
    }

    private void run(Supplier<Boolean> stopCondition) throws IOException {
        while (!Thread.currentThread().isInterrupted()) {
            processFiles();

            if (stopCondition.get()) {
                processFiles();
                break;
            }

            try {
                Thread.sleep(maxDelay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void processFiles() throws IOException {
        Files.walkFileTree(watchDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (watchDir.equals(dir)) {
                    return FileVisitResult.CONTINUE;
                }
                return FileVisitResult.SKIP_SUBTREE;
            }

            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (ignoreFiles.contains(file)) {
                    return FileVisitResult.CONTINUE;
                }

                FileEntry<T> filePointer = filePointers.get(file);
                if (filePointer == null) {
                    T fileName = fileNameParser.parse(file);
                    if (fileName == null) {
                        ignoreFiles.add(file);
                        return FileVisitResult.CONTINUE;
                    }

                    boolean success = listener.onNewFile(fileName);
                    if (!success) {
                        return FileVisitResult.CONTINUE;
                    }
                    filePointer = FileEntry.of(fileName, 0L);
                    filePointers.put(file, filePointer);
                }

                if (isChanged(file, filePointer.pointer())) {
                    long newPos = notifyChanged(file, filePointer);
                    if (newPos == -1) {
                        deleteFile(file);
                        filePointers.remove(file);
                        return FileVisitResult.CONTINUE;
                    } else if (newPos > 0) {
                        filePointers.put(file, FileEntry.of(filePointer.name(), newPos));
                    }
                }

                return FileVisitResult.CONTINUE;
            }
        });
    }

    public boolean isChanged(Path path, long totalRead) {
        try {
            return (Files.size(path) > totalRead);
        } catch (IOException e) {
            log.warn("isChanged ['{}'] -> error: {}", path, e.getMessage());
            return false;
        }
    }

    private long notifyChanged(Path path, FileEntry<T> fileEntry) {
        try {
            RandomAccessFile file = fileCache.get(path);
            file.seek(fileEntry.pointer());
            long newPos = listener.onChanged(fileEntry.name(), file);
            if (newPos == -1) {
                fileCache.close(path);
            }
            return newPos;
        } catch (IOException e) {
            log.error("processChanged ['{}'] -> error: {}", path, e.getMessage());
        }
        return 0L;
    }

    private static void deleteFile(Path path) {
        try {
            Files.delete(path);
        } catch (IOException e) {
            log.warn("deleteFile ['{}'] -> error: {}", path, e.getMessage());
        }
    }

    public interface FileListener<T> {

        boolean onNewFile(T fileName);

        /**
         * @return new file offset or -1 if file no longer tracked (e.g. all file read)
         */
        long onChanged(T fileName, RandomAccessFile in) throws IOException;
    }

    public interface FileNameParser<T> {

        /**
         * @return null if file should be ignored
         */
        T parse(Path path);
    }

    public interface FileReader {

        /**
         * @return new file offset
         */
        long read(RandomAccessFile in, ChunkConsumer consumer) throws IOException;
    }

    public static class ByteArrayFileReader implements FileReader {

        private final byte[] dataBuffer = new byte[8192];

        @Override
        public long read(RandomAccessFile in, ChunkConsumer consumer) throws IOException {
            long result = in.getFilePointer();

            try {
                while (!Thread.currentThread().isInterrupted()) {
                    int read = in.read(dataBuffer, 0, dataBuffer.length);
                    if (read <= 0) {
                        break;
                    }

                    int consumed = consumer.consume(new Chunk(dataBuffer, read));
                    if (consumed == -1) {
                        return -1;
                    }
                    result += consumed;
                    in.seek(result);
                }
            } catch (IOException e) {
                log.warn("read error: {}", e.getMessage());
            }

            return result;
        }
    }

    private static class FileCache implements Closeable {

        private final LoadingCache<Path, RandomAccessFile> cache;

        public FileCache() {
            this.cache = CacheBuilder.newBuilder()
                    .maximumSize(MAX_OPEN_FILES)
                    .removalListener((RemovalListener<Path, RandomAccessFile>) notification -> {
                        try {
                            notification.getValue().close();
                            log.debug("closing: {}", notification.getKey());
                        } catch (IOException e) {
                            log.warn("close error: {}", e.getMessage());
                        }
                    })
                    .build(new CacheLoader<Path, RandomAccessFile>() {

                        @Override
                        public RandomAccessFile load(Path key) throws Exception {
                            return new RandomAccessFile(key.toFile(), "r");
                        }
                    });
        }

        public RandomAccessFile get(Path path) {
            return cache.getUnchecked(path);
        }

        public void close(Path path) {
            cache.invalidate(path);
        }

        @Override
        public void close() {
            cache.invalidateAll();
        }
    }

    public static class Chunk {

        private final byte[] ab;
        private final int len;

        protected Chunk(byte[] ab, int len) { // NOSONAR
            this.ab = ab;
            this.len = len;
        }

        public byte[] bytes() {
            return ab;
        }

        public int len() {
            return len;
        }
    }

    public interface ChunkConsumer {

        int consume(Chunk chunk) throws IOException;
    }

    @Value.Immutable
    public interface FileEntry<T> {

        @Value.Parameter
        T name();

        @Value.Parameter
        long pointer();

        static <T> FileEntry<T> of(T name, long pointer) {
            return ImmutableFileEntry.of(name, pointer);
        }
    }
}
