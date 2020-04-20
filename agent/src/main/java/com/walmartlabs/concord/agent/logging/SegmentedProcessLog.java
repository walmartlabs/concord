package com.walmartlabs.concord.agent.logging;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SegmentedProcessLog extends RedirectedProcessLog {

    private static final Logger log = LoggerFactory.getLogger(SegmentedProcessLog.class);

    private final Path logsDir;
    private final Map<Path, LogSegment> segments;
    private final Set<Path> invalidFiles = new HashSet<>();

    private final byte[] logBuffer = new byte[8192];

    public SegmentedProcessLog(Path logsDir, UUID instanceId, LogAppender appender, long logSteamMaxDelay) throws IOException {
        super(logsDir, instanceId, appender, logSteamMaxDelay);
        this.logsDir = logsDir;
        this.segments = new LinkedHashMap<>();
    }

    @Override
    public void run(Supplier<Boolean> stopCondition) throws Exception {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                collectSegments();

                int read = processSegments(stopCondition);
                if (read > 0) {
                    continue;
                }

                if (stopCondition.get() && segments.values().stream().allMatch(LogSegment::isClosed)) {
                    break;
                }

                // job is still running, wait for more data
                try {
                    Thread.sleep(logSteamMaxDelay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } finally {
            cleanup();
        }
    }

    @Override
    public void delete() {
        super.delete();
        try {
            IOUtils.deleteRecursively(logsDir);
        } catch (IOException e) {
            log.warn("delete -> error while removing a log directory: {}", logsDir);
        }
    }

    private void collectSegments() throws IOException {
        Files.walkFileTree(logsDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (dir.equals(logsDir)) {
                    return FileVisitResult.CONTINUE;
                }
                return FileVisitResult.SKIP_SUBTREE;
            }

            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (segments.containsKey(file) || invalidFiles.contains(file)) {
                    return FileVisitResult.CONTINUE;
                }

                String segmentFileName = file.getFileName().toString();
                Segment segment = parse(segmentFileName);
                if (segment == null) {
                    invalidFiles.add(file);
                    return FileVisitResult.CONTINUE;
                }

                Long segmentId = createSegment(segment);
                if (segmentId != null) {
                    segments.put(file, new LogSegment(segmentId, Files.newInputStream(file)));
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private int processSegments(Supplier<Boolean> stopCondition) {
        int total = 0;

        for (Map.Entry<Path, LogSegment> entry : segments.entrySet()) {
            LogSegment segment = entry.getValue();

            try {
                int read = processSegment(segment, chunk -> {
                    byte[] chunkBuffer = new byte[chunk.len()];
                    System.arraycopy(chunk.bytes(), 0, chunkBuffer, 0, chunk.len());
                    appender.appendLog(instanceId, segment.getId(), chunkBuffer);
                });

                if (read > 0) {
                    total += read;
                }

                if (stopCondition.get() && segment.totalRead() >= Files.size(entry.getKey())) {
                    segment.close();
                }
            } catch (IOException e) {
                log.error("processSegment ['{}'] -> error", entry.getKey());
            }
        }

        return total;
    }

    private int processSegment(LogSegment segment, Consumer<Chunk> sink) throws IOException {
        int total = 0;
        while (!Thread.currentThread().isInterrupted()) {
            int read = segment.read(logBuffer);
            if (read > 0) {
                total += read;
                sink.accept(new Chunk(logBuffer, read));
            } else {
                break;
            }
        }
        return total;
    }

    private void cleanup() {
        for (Map.Entry<Path, LogSegment> e : segments.entrySet()) {
            e.getValue().close();
        }
        segments.clear();
    }

    private static Segment parse(String segmentFileName) {
        if (segmentFileName.length() < 36) {
            log.warn("createSegment ['{}'] -> invalid segment file name: no uuid", segmentFileName);
            return null;
        }

        try {
            UUID correlationId = UUID.fromString(segmentFileName.substring(0, 36));

            if (!segmentFileName.endsWith(".log")) {
                log.warn("createSegment ['{}'] -> invalid segment file name: invalid extension", segmentFileName);
                return null;
            }

            if (segmentFileName.length() - 37 - ".log".length() <= 0) {
                log.warn("createSegment ['{}'] -> invalid segment file name: no name", segmentFileName);
                return null;
            }
            String segmentName = segmentFileName.substring(37, segmentFileName.length() - ".log".length());

            return new Segment(correlationId, segmentName);
        } catch (Exception e) {
            log.warn("createSegment ['{}'] -> error: {}", segmentFileName, e.getMessage());
        }
        return null;
    }

    private Long createSegment(Segment segment) {
        try {
            return appender.createSegment(instanceId, segment.getCorrelationId(), segment.getName());
        } catch (Exception e) {
            log.warn("createSegment ['{}'] -> error: {}", segment, e.getMessage());
        }
        return null;
    }

    static class Segment {

        private final UUID correlationId;
        private final String name;

        public Segment(UUID correlationId, String name) {
            this.correlationId = correlationId;
            this.name = name;
        }

        public UUID getCorrelationId() {
            return correlationId;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return "Segment{" +
                    "correlationId=" + correlationId +
                    ", name='" + name + '\'' +
                    '}';
        }
    }

    static class LogSegment {

        private final long id;
        private InputStream in;
        private int totalRead;

        public LogSegment(long id, InputStream in) {
            this.id = id;
            this.in = in;
        }

        public int read(byte[] ab) throws IOException {
            if (in == null) {
                return 0;
            }

            int result = in.read(ab, 0, ab.length);
            if (result > 0) {
                totalRead += result;
            }
            return result;
        }

        public long getId() {
            return id;
        }

        public int totalRead() {
            return totalRead;
        }

        public void close() {
            if (in == null) {
                return;
            }

            try {
                in.close();
                in = null;
            } catch (Exception e) {
                // ignore
            }
        }

        public boolean isClosed() {
            return in == null;
        }
    }
}
