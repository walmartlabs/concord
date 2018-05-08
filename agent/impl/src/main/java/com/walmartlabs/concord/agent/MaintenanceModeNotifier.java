package com.walmartlabs.concord.agent;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardWatchEventKinds.*;

public class MaintenanceModeNotifier implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceModeNotifier.class);

    private static final long RETRY_DELAY = TimeUnit.SECONDS.toMillis(5);

    private final Path file;
    private final MaintenanceModeListener listener;

    public MaintenanceModeNotifier(Path file, MaintenanceModeListener listener) {
        this.file = file;
        this.listener = listener;
    }

    @Override
    public void run() {
        log.info("run ['{}'] -> started", file);
        while (!Thread.currentThread().isInterrupted()) {
            try {
                waitFile(file);
                log.info("Maintenance: {}", Files.readAllLines(file));
                listener.onMaintenanceMode();
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("run -> interrupted");
            } catch (Exception e) {
                log.error("run -> error, retry after {} sec", RETRY_DELAY / 1000, e);
                sleep(RETRY_DELAY);
            }
        }
    }

    private static void waitFile(Path file) throws IOException, InterruptedException {
        Path path = file.getParent();
        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
            path.register(watcher, ENTRY_CREATE, ENTRY_MODIFY);
            while (true) {
                WatchKey key = watcher.take();

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind kind = event.kind();

                    if (kind == OVERFLOW) {
                        log.info("waitFile ['{}'] -> overflow, ok:)", file);
                        continue;
                    }

                    final Path changed = path.resolve((Path) event.context());
                    if (changed.equals(file)) {
                        log.info("waitFile ['{}'] -> done", file);
                        return;
                    }
                }

                boolean valid = key.reset();
                if (!valid) {
                    throw new IOException("watch directory inaccessible");
                }
            }
        }
    }

    private static void sleep(long t) {
        try {
            Thread.sleep(t);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
