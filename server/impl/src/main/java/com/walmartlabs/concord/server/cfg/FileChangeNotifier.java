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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class FileChangeNotifier {

    interface FileChangeListener {

        void onChange(Path file);
    }

    private static final Logger log = LoggerFactory.getLogger(FileChangeNotifier.class);

    private static final long ERROR_DELAY = TimeUnit.SECONDS.toMillis(30);
    private static final long WATCH_INTERVAL = TimeUnit.MINUTES.toMillis(1);

    private final Path path;
    private final FileChangeListener listener;

    private Thread worker;

    private byte[] prevFileDigest;

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
                if (isFileChanged(path)) {
                    listener.onChange(path);
                }
                sleep(WATCH_INTERVAL);
            } catch (Exception e) {
                log.warn("run -> error: {}. Will retry in {}ms...", e.getMessage(), ERROR_DELAY, e);
                sleep(ERROR_DELAY);
            }
        }
    }

    private boolean isFileChanged(Path file) throws IOException, NoSuchAlgorithmException {
        if (Files.notExists(file)) {
            return false;
        }

        byte[] currentDigest = getMD5(file);

        boolean result = !Arrays.equals(prevFileDigest, currentDigest);
        this.prevFileDigest = currentDigest;
        return result;
    }

    private static byte[] getMD5(Path file) throws NoSuchAlgorithmException, IOException {
        MessageDigest digest = MessageDigest.getInstance("MD5");

        byte[] buffer = new byte[8192];
        int read;
        try (InputStream is = Files.newInputStream(file)) {
            while ((read = is.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            return digest.digest();
        } catch (FileNotFoundException e) {
            return null;
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
