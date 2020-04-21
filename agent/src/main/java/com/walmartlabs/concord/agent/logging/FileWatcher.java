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

import com.google.common.cache.*;
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

public class FileWatcher implements Closeable {

    public static void watch(Path path, Supplier<Boolean> stopCondition, long maxDelay, FileListener listener) throws IOException {
        try (FileWatcher watcher = new FileWatcher(path, maxDelay, listener)) {
            watcher.run(stopCondition);
        }
    }

    private static final Logger log = LoggerFactory.getLogger(FileWatcher.class);

    private static final int MAX_OPEN_FILES = 10;

    private final Path watchDir;
    private final long maxDelay;
    private final FileListener listener;

    private final FileReader fileReader = new FileReader();
    private final Map<Path, FileEntry> files = new HashMap<>();
    private final Set<Path> ignoreFiles = new HashSet<>();
    private final byte[] dataBuffer = new byte[8192];

    public FileWatcher(Path watchDir, long maxDelay, FileListener listener) {
        this.watchDir = watchDir;
        this.maxDelay = maxDelay;
        this.listener = listener;
    }

    @Override
    public void close() {
        fileReader.close();
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

                FileEntry entry = files.get(file);
                if (entry == null) {
                    entry = processNewFile(file);
                    if (entry == null) {
                        return FileVisitResult.CONTINUE;
                    } else {
                        files.put(file, entry);
                    }
                }

                if (entry.isChanged()) {
                    processChanged(entry);
                }

                return FileVisitResult.CONTINUE;
            }
        });
    }

    private FileEntry processNewFile(Path file) {
        FileListener.Result result = listener.onNewFile(file);
        switch (result) {
            case OK: {
                return new FileEntry(file, fileReader);
            }
            case IGNORE: {
                ignoreFiles.add(file);
                return null;
            }
            case ERROR: {
                return null;
            }
            default:
                throw new IllegalStateException("Unknown result: " + result);
        }
    }

    private void processChanged(FileEntry entry) {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                int read = entry.read(dataBuffer, dataBuffer.length);
                if (read <= 0) {
                    break;
                }

                listener.onNewData(entry.path, dataBuffer, read);
            }
        } catch (IOException e) {
            log.error("processChanged ['{}'] -> error: {}", entry, e.getMessage());
        }
    }

    public interface FileListener {

        enum Result {
            OK,
            ERROR,
            IGNORE
        }

        Result onNewFile(Path file);

        void onNewData(Path file, byte[] data, int len);
    }

    private static class FileEntry {

        private final Path path;

        private final FileReader fileReader;

        private long totalRead = 0;

        public FileEntry(Path path, FileReader fileReader) {
            this.path = path;
            this.fileReader = fileReader;
        }

        public int read(byte[] ab, int len) throws IOException {
            int result = fileReader.read(path, totalRead, ab, len);
            if (result > 0) {
                totalRead += result;
            }
            return result;
        }

        public boolean isChanged() {
            try {
                return (Files.size(path) > totalRead);
            } catch (IOException e) {
                log.warn("isChanged ['{}'] -> error: {}", path, e.getMessage());
                return false;
            }
        }
    }

    private static class FileReader implements Closeable {

        private final LoadingCache<Path, RandomAccessFile> cache;

        public FileReader() {
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

        public int read(Path file, long from, byte[] ab, int len) throws IOException {
            RandomAccessFile raf = cache.getUnchecked(file);
            raf.seek(from);
            return raf.read(ab, 0, len);
        }

        @Override
        public void close() {
            cache.invalidateAll();
        }
    }
}
