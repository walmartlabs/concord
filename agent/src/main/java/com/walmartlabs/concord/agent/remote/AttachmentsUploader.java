package com.walmartlabs.concord.agent.remote;

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

import com.walmartlabs.concord.agent.cfg.AgentConfiguration;
import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.common.PathUtils;
import com.walmartlabs.concord.common.TemporaryPath;
import com.walmartlabs.concord.common.ZipUtils;
import com.walmartlabs.concord.sdk.Constants;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.UUID;

public class AttachmentsUploader {

    private final ApiClient apiClient;
    private final Duration timeout;

    @Inject
    public AttachmentsUploader(ApiClient apiClient, AgentConfiguration cfg) {
        this(apiClient, cfg.getAttachmentsUploadTimeout());
    }

    public AttachmentsUploader(ApiClient apiClient, Duration timeout) {
        this.apiClient = apiClient;
        this.timeout = timeout;
    }

    public void upload(UUID instanceId, Path workDir) throws Exception {
        Path attachmentsDir = workDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME);
        if (!Files.exists(attachmentsDir)) {
            return;
        }

        RemoteOperations.call(timeout, () -> {
            uploadArchive(instanceId, attachmentsDir);
            return null;
        });
    }

    private void uploadArchive(UUID instanceId, Path attachmentsDir) throws Exception {
        try (TemporaryPath tmp = PathUtils.tempFile("attachments", ".zip")) {
            try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(Files.newOutputStream(tmp.path()))) {
                Files.walkFileTree(attachmentsDir, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        // Follow symlinks when checking the type, just as zipFile does when reading.
                        // Opening a FIFO can block without responding to the upload deadline's interrupt.
                        if (!Files.isRegularFile(file)) {
                            throw new IOException("Attachment is not a regular file: " + file);
                        }
                        ZipUtils.zipFile(zip, file, attachmentsDir.relativize(file).toString());
                        return FileVisitResult.CONTINUE;
                    }
                });
            }

            ProcessApi api = new ProcessApi(apiClient);
            RemoteOperations.withRetry(() -> {
                try (InputStream input = Files.newInputStream(tmp.path())) {
                    api.uploadProcessAttachments(instanceId, input);
                    return null;
                }
            });
        }
    }
}
