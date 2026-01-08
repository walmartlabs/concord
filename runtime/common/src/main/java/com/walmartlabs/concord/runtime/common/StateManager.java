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

import com.walmartlabs.concord.common.ObjectInputStreamWithClassLoader;
import com.walmartlabs.concord.common.PathUtils;
import com.walmartlabs.concord.common.TemporaryPath;
import com.walmartlabs.concord.sdk.Constants;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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

        saveProcessState(baseDir, state);
    }

    public static void cleanupState(Path baseDir) throws IOException {
        Path stateDir = baseDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(Constants.Files.JOB_STATE_DIR_NAME);

        PathUtils.deleteRecursively(stateDir);

        Path sessionFilesDir = baseDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(Constants.Files.JOB_SESSION_FILES_DIR_NAME);
        PathUtils.deleteRecursively(sessionFilesDir);
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

    /**
     * Reads a serialized process state object from
     * the standard location inside the provided {@code baseDir}.
     */
    @SuppressWarnings("unchecked")
    public static <T extends Serializable> T readProcessState(Path baseDir, ClassLoader cl) {
        Path stateDir = baseDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(Constants.Files.JOB_STATE_DIR_NAME);

        Path p = stateDir.resolve("instance");
        if (Files.notExists(p)) {
            return null;
        }

        try (ObjectInputStream in = new ObjectInputStreamWithClassLoader(Files.newInputStream(p), cl)) {
            return (T) in.readObject();
        } catch (ClassNotFoundException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Serializes the specified process state object into a file
     * in the standard location inside the provided {@code baseDir}.
     */
    public static void saveProcessState(Path baseDir, Serializable state) throws IOException {
        Path stateDir = baseDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(Constants.Files.JOB_STATE_DIR_NAME);

        if (Files.notExists(stateDir)) {
            Files.createDirectories(stateDir);
        }

        Path dst = stateDir.resolve("instance");

        try (TemporaryPath tmp = PathUtils.tempFile("instance", "state");
             OutputStream out = Files.newOutputStream(tmp.path())) {

            SerializationUtils.serialize(out, state);
            Files.move(tmp.path(), dst, REPLACE_EXISTING);
        }
    }

    public static void persist(Path baseDir, String storeName, Serializable object) throws IOException {
        Path storageDir = baseDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve("storage"); // TODO: constants

        if (!Files.exists(storageDir)) {
            Files.createDirectories(storageDir);
        }

        Path storage = storageDir.resolve(storeName);
        try (OutputStream out = Files.newOutputStream(storage, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            SerializationUtils.serialize(out, object);
        }
    }

    public static <T extends Serializable> T load(Path baseDir, String storageName, Class<T> expectedType) {
        Path storage = baseDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve("storage")
                .resolve(storageName); // TODO: constants

        if (Files.notExists(storage)) {
            return null;
        }

        try (InputStream in = Files.newInputStream(storage)) {
            return SerializationUtils.deserialize(in, expectedType);
        } catch (IOException e) {
            throw new RuntimeException("Error while reading persisted storage " + storageName + ": " + e.getMessage(), e);
        }
    }

    private StateManager() {
    }
}
