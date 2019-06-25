package com.walmartlabs.concord.server.cfg;

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
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

public class FileChangeNotifier {

    interface FileChangeListener {

        void onChange(Path file);
    }

    private static final Logger log = LoggerFactory.getLogger(FileChangeNotifier.class);

    private static final long ERROR_DELAY = TimeUnit.SECONDS.toMillis(30);

    private final Path path;
    private final FileChangeListener listener;

    private Thread worker;

    public FileChangeNotifier(Path path, FileChangeListener listener) {
        this.path = path;
        this.listener = listener;
    }

    public void start() {
        this.worker = new Thread(this::run, "File " + path.getFileName() + " change notifier");
        this.worker.start();
    }

    public void stop() {
        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
    }

    private void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                watchChanges(path, listener);
            } catch (UnsupportedOperationException e) {
                log.error("run -> error: watching not supported");
                return;
            } catch (Exception e) {
                log.warn("run -> error: {}. Will retry in {}ms...", e.getMessage(), ERROR_DELAY, e);
                sleep(ERROR_DELAY);
            }
        }
    }

    private static void watchChanges(Path file, FileChangeListener listener) throws IOException, InterruptedException {
        Path dirPath = file.getParent();
        String fileName = file.getFileName().toString();

        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
            WatchKey watchKey = dirPath.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);

            while (!Thread.currentThread().isInterrupted()) {
                WatchKey wk = watcher.take();
                boolean isChanged = false;
                for (WatchEvent<?> event : wk.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == OVERFLOW) {
                        continue;
                    }

                    Path changed = (Path) event.context();
                    if (fileName.equals(changed.getFileName().toString())) {
                        isChanged = true;
                    }
                }

                if (isChanged) {
                    listener.onChange(file);
                }

                boolean valid = wk.reset();
                if (!valid) {
                    log.warn("watchChanges -> watch key has been unregistered");
                    break;
                }
            }
            watchKey.reset();
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
