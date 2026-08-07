package com.walmartlabs.concord.runtime.v25.runner.persistence;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public final class FileCheckpointStore implements CheckpointStore {

    private final Path target;
    private final State25Codec codec;

    public FileCheckpointStore(Path target) {
        this(target, Thread.currentThread().getContextClassLoader());
    }

    public FileCheckpointStore(Path target, ClassLoader classLoader) {
        this.target = target.toAbsolutePath().normalize();
        this.codec = new State25Codec(classLoader);
    }

    @Override
    public void save(String name, State25 state) throws IOException {
        var parent = target.getParent();
        Files.createDirectories(parent);
        var temporary = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
        try {
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                codec.write(Channels.newOutputStream(channel), state);
                channel.force(true);
            }
            Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING);
            forceDirectory(parent);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Override
    public State25 load() throws IOException {
        try (var input = Files.newInputStream(target)) {
            return codec.read(input);
        } catch (NoSuchFileException e) {
            return null;
        }
    }

    /**
     * Returns an identifier for the exact durable state generation currently on disk.
     */
    public String generation() throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(target));
            var result = new StringBuilder(digest.length * 2);
            for (var value : digest) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (NoSuchFileException e) {
            return null;
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is unavailable", e);
        }
    }

    private static void forceDirectory(Path directory) throws IOException {
        try (var channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        }
    }
}
