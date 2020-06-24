package com.walmartlabs.concord.agent.postprocessing;

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

import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.agent.ExecutionException;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.common.TemporaryPath;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public abstract class JobFileUploadPostProcessor implements JobPostProcessor {

    private final String sourcePath;
    private final String name;
    private final Uploader uploader;

    public JobFileUploadPostProcessor(String sourcePath, String name, Uploader uploader) {
        this.sourcePath = sourcePath;
        this.name = name;
        this.uploader = uploader;
    }

    @Override
    public void process(UUID instanceId, Path payloadDir) throws JobPostProcessorException {
        Path attachmentsDir = payloadDir.resolve(sourcePath);
        if (!Files.exists(attachmentsDir)) {
            return;
        }

        try (TemporaryPath tmp = IOUtils.tempFile(name, ".zip")) {
            try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(Files.newOutputStream(tmp.path()))) {
                IOUtils.zip(zip, attachmentsDir);
            }

            uploader.upload(instanceId, tmp.path());
        } catch (IOException | ApiException e) {
            throw new JobPostProcessorException("Error while processing " + name + ": " + e.getMessage());
        }
    }

    public interface Uploader {

        void upload(UUID instanceId, Path data) throws ApiException;
    }
}
