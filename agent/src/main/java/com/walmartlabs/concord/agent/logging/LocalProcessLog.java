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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Local log file. Typically used as a temporary buffer to store process logs
 * before sending them to the server.
 */
public class LocalProcessLog extends AbstractProcessLog {

    private static final Logger log = LoggerFactory.getLogger(LocalProcessLog.class);

    private final Path baseDir;

    public LocalProcessLog(Path baseDir) throws IOException {
        this.baseDir = baseDir;
        Files.createFile(logFile());
    }

    @Override
    public void delete() {
        Path p = logFile();
        if (Files.exists(p)) {
            try {
                Files.delete(p);
            } catch (IOException e) {
                log.warn("delete -> error while removing a log file: {}", p);
            }
        }
    }

    @Override
    public void log(InputStream src) throws IOException {
        Path f = logFile();
        try (OutputStream dst = Files.newOutputStream(f, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            src.transferTo(dst);
        }
    }

    @Override
    protected void log(String message) {
        Path f = logFile();
        try (OutputStream out = Files.newOutputStream(f, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            out.write(message.getBytes());
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException("Error writing to a log file: " + f, e);
        }
    }

    public Path logFile() {
        return baseDir.resolve("system" + ".log");
    }
}
