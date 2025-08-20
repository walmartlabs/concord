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

import com.walmartlabs.concord.agent.AgentConstants;
import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.common.PathUtils;
import com.walmartlabs.concord.common.TemporaryPath;
import com.walmartlabs.concord.common.ZipUtils;
import com.walmartlabs.concord.sdk.Constants;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import javax.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public class AttachmentsUploader {

    private final ApiClient apiClient;

    @Inject
    public AttachmentsUploader(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public void upload(UUID instanceId, Path workDir) throws Exception {
        Path attachmentsDir = workDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME);
        if (!Files.exists(attachmentsDir)) {
            return;
        }

        try (TemporaryPath tmp = PathUtils.tempFile("attachments", ".zip")) {
            try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(Files.newOutputStream(tmp.path()))) {
                ZipUtils.zip(zip, attachmentsDir);
            }

            ProcessApi api = new ProcessApi(apiClient);
            ClientUtils.withRetry(AgentConstants.API_CALL_MAX_RETRIES, AgentConstants.API_CALL_RETRY_DELAY, () -> {
                api.uploadProcessAttachments(instanceId, Files.newInputStream(tmp.path()));
                return null;
            });
        }
    }
}
