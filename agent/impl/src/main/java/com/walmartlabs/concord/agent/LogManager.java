package com.walmartlabs.concord.agent;

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

import com.walmartlabs.concord.common.LogUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

import static com.walmartlabs.concord.common.LogUtils.LogLevel;

public class LogManager {

    private static final Logger log = LoggerFactory.getLogger(LogManager.class);

    private final Configuration cfg;

    public LogManager(Configuration cfg) {
        this.cfg = cfg;
    }

    public void delete(UUID id) {
        Path f = logFile(id);
        if (Files.exists(f)) {
            try {
                Files.delete(f);
            } catch (IOException e) {
                throw new RuntimeException("Error removing a log file: " + f, e);
            }
        }
    }

    public void log(UUID id, String log, Object... args) {
        if (args != null && args.length > 0) {
            int last = args.length - 1;
            Object o = args[last];
            if (o instanceof Throwable) {
                StringWriter sw = new StringWriter();
                PrintWriter w = new PrintWriter(sw);
                ((Throwable) o).printStackTrace(w);
                args[last] = sw.toString();
            }
        }

        Path f = logFile(id);
        try (OutputStream out = Files.newOutputStream(f, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            String s = String.format(log + "\n", args);
            out.write(s.getBytes());
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException("Error writing to a log file: " + f, e);
        }
    }

    public void info(UUID id, String log, Object... args) {
        log(id, LogUtils.formatMessage(LogLevel.INFO, log, args));
    }

    public void warn(UUID id, String log, Object... args) {
        log(id, LogUtils.formatMessage(LogLevel.WARN, log, args));
    }

    public void error(UUID id, String log, Object... args) {
        log(id, LogUtils.formatMessage(LogLevel.ERROR, log, args));
    }

    public void log(UUID id, InputStream src) throws IOException {
        Path f = logFile(id);
        try (OutputStream dst = Files.newOutputStream(f, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
             BufferedReader reader = new BufferedReader(new InputStreamReader(src))) {

            String line;
            while ((line = reader.readLine()) != null) {
                dst.write(line.getBytes());
                dst.write('\n');
                dst.flush();
            }
        }
    }

    public void touch(UUID id) {
        Path f = logFile(id);
        if (!Files.exists(f)) {
            try {
                Files.createFile(f);
                log.info("store ['{}'] -> storing into {}", id, f);
            } catch (IOException e) {
                throw new RuntimeException("Error opening a log file: " + f, e);
            }
        }
    }

    public Path open(UUID id) {
        return logFile(id);
    }

    private void log(UUID id, String message) {
        Path f = logFile(id);
        try (OutputStream out = Files.newOutputStream(f, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            out.write(message.getBytes());
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException("Error writing to a log file: " + f, e);
        }
    }

    private Path logFile(UUID id) {
        Path baseDir = cfg.getLogDir();
        return baseDir.resolve(id + ".log");
    }
}
