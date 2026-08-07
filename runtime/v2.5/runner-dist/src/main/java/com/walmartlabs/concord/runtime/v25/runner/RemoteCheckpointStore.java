package com.walmartlabs.concord.runtime.v25.runner;

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

import com.walmartlabs.concord.client2.ApiClient;
import com.walmartlabs.concord.client2.ApiException;
import com.walmartlabs.concord.client2.CheckpointApi;
import com.walmartlabs.concord.client2.ClientUtils;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.v25.runner.persistence.CheckpointStore;
import com.walmartlabs.concord.runtime.v25.runner.persistence.State25;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.runtime.v2.sdk.SensitiveDataHolder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Uploads durable named checkpoints immediately after writing their local state. */
final class RemoteCheckpointStore implements CheckpointStore {

    private final CheckpointStore delegate;
    private final Path stateFile;
    private final RunnerConfiguration configuration;
    private final UUID instanceId;
    private final CheckpointApi api;
    private final RunnerWiring.PersistenceService persistenceService;
    private final SensitiveDataHolder sensitiveData;

    RemoteCheckpointStore(CheckpointStore delegate, Path stateFile, RunnerConfiguration configuration,
                          UUID instanceId, ApiClient client, RunnerWiring.PersistenceService persistenceService,
                          SensitiveDataHolder sensitiveData) {
        this.delegate = delegate;
        this.stateFile = stateFile;
        this.configuration = configuration;
        this.instanceId = instanceId;
        this.api = new CheckpointApi(client);
        this.persistenceService = persistenceService;
        this.sensitiveData = sensitiveData;
    }

    @Override
    public void save(String name, State25 state) throws IOException {
        delegate.save(name, state);
        persistenceService.persistSessionFile(Constants.Files.SENSITIVE_DATA_FILE_NAME, sensitiveData.get());
        if ("suspend".equals(name)) {
            return;
        }
        var archive = archive(stateFile);
        var request = new LinkedHashMap<String, Object>();
        request.put("id", UUID.randomUUID());
        request.put("correlationId", UUID.randomUUID());
        request.put("name", name);
        request.put("data", archive);
        try {
            ClientUtils.withRetry(configuration.api().retryCount(), configuration.api().retryInterval(), () -> {
                api.uploadCheckpoint(instanceId, request);
                return null;
            });
        } catch (ApiException e) {
            throw new IOException("Cannot upload checkpoint '" + name + "'", e);
        } finally {
            Files.deleteIfExists(archive);
        }
    }

    @Override
    public State25 load() throws IOException {
        return delegate.load();
    }

    static Path archive(Path stateFile) throws IOException {
        var archive = Files.createTempFile("concord-v2.5-checkpoint-", ".zip");
        var stateEntry = Constants.Files.JOB_ATTACHMENTS_DIR_NAME + "/" + Constants.Files.JOB_STATE_DIR_NAME
                + "/" + stateFile.getFileName();
        try (var output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry(stateEntry));
            Files.copy(stateFile, output);
            output.closeEntry();
            addSystemDirectory(stateFile, output);
        } catch (UncheckedIOException e) {
            Files.deleteIfExists(archive);
            throw e.getCause();
        }
        return archive;
    }

    private static void addSystemDirectory(Path stateFile, ZipOutputStream output) throws IOException {
        var stateDirectory = stateFile.getParent();
        if (stateDirectory == null || stateDirectory.getParent() == null || stateDirectory.getParent().getParent() == null) {
            return;
        }
        var systemDirectory = stateDirectory.getParent().getParent().resolve(Constants.Files.CONCORD_SYSTEM_DIR_NAME);
        if (Files.notExists(systemDirectory)) {
            return;
        }
        try (var paths = Files.walk(systemDirectory)) {
            paths.forEach(path -> {
                var entry = systemDirectory.getParent().relativize(path).toString().replace('\\', '/');
                if (Files.isDirectory(path)) {
                    entry += "/";
                }
                try {
                    output.putNextEntry(new ZipEntry(entry));
                    if (Files.isRegularFile(path)) {
                        Files.copy(path, output);
                    }
                    output.closeEntry();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }
}
