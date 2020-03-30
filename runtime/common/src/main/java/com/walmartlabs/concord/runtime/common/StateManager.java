package com.walmartlabs.concord.runtime.common;

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

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.sdk.Constants;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

// TODO rename to PersistenceManager?
public final class StateManager {

    private static final String RESUME_MARKER = Constants.Files.RESUME_MARKER_FILE_NAME;
    private static final String SUSPEND_MARKER = Constants.Files.SUSPEND_MARKER_FILE_NAME;

    public static void finalizeSuspendedState(Path baseDir, Serializable state, Set<String> eventNames) throws IOException {
        Path stateDir = baseDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(Constants.Files.JOB_STATE_DIR_NAME);

        Path resumeMarker = stateDir.resolve(RESUME_MARKER);
        if (Files.exists(resumeMarker)) {
            Files.delete(resumeMarker);
        }

        Path suspendMarker = stateDir.resolve(SUSPEND_MARKER);
        if (Files.exists(suspendMarker)) {
            Files.delete(suspendMarker);
        }

        if (Files.notExists(stateDir)) {
            Files.createDirectories(stateDir);
        }

        Path marker = stateDir.resolve(SUSPEND_MARKER);
        Files.write(marker, eventNames);

        saveState(baseDir, state);
    }

    public static void cleanupState(Path baseDir) throws IOException {
        Path stateDir = baseDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(Constants.Files.JOB_STATE_DIR_NAME);

        IOUtils.deleteRecursively(stateDir);
    }

    public static void saveResumeEvent(Path baseDir, String eventName) {
        Path dst = baseDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(Constants.Files.JOB_STATE_DIR_NAME)
                .resolve(Constants.Files.RESUME_MARKER_FILE_NAME);

        Path parent = dst.getParent();

        try {
            if (!Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            Files.write(dst, eventName.getBytes());
        } catch (IOException e) {
            throw new RuntimeException("Error while saving a resume event: " + e.getMessage(), e);
        }
    }

    public static Set<String> readResumeEvents(Path baseDir) {
        Path p = baseDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(Constants.Files.JOB_STATE_DIR_NAME)
                .resolve(Constants.Files.RESUME_MARKER_FILE_NAME);

        if (!Files.exists(p)) {
            return null;
        }

        try {
            return new HashSet<>(Files.readAllLines(p));
        } catch (IOException e) {
            throw new RuntimeException("Error while reading a resume event: " + e.getMessage(), e);
        }
    }

    public static <T> T readState(Path baseDir, Class<T> type) {
        Path stateDir = baseDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(Constants.Files.JOB_STATE_DIR_NAME);

        Path p = stateDir.resolve("instance");
        if (Files.notExists(p)) {
            throw new IllegalStateException("Can't read the state file. File not found: " + p);
        }

        try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(p))) {
            return type.cast(in.readObject());
        } catch (ClassNotFoundException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void archive(Path baseDir, Serializable state, Path result) throws IOException {
        saveState(baseDir, state);

        try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(Files.newOutputStream(result))) {
            zip(zip, Constants.Files.JOB_ATTACHMENTS_DIR_NAME + "/", baseDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME));
            zip(zip, Constants.Files.CONCORD_SYSTEM_DIR_NAME + "/", baseDir.resolve(Constants.Files.CONCORD_SYSTEM_DIR_NAME));
        }
    }

    private static void zip(ZipArchiveOutputStream zip, String name, Path src) throws IOException {
        if (Files.notExists(src)) {
            return;
        }
        IOUtils.zip(zip, name, src);
    }

    private static void saveState(Path baseDir, Serializable state) throws IOException {
        Path stateDir = baseDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(Constants.Files.JOB_STATE_DIR_NAME);

        if (Files.notExists(stateDir)) {
            Files.createDirectories(stateDir);
        }

        Path dst = stateDir.resolve("instance");

        Path tmp = IOUtils.createTempFile("instance", "state");
        try (OutputStream out = Files.newOutputStream(tmp)) {
            SerializationUtils.serialize(out, state);
        }
        Files.move(tmp, dst, REPLACE_EXISTING);
    }

    private StateManager() {
    }
}
