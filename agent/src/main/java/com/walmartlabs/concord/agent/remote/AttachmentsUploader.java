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

import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.agent.AgentConstants;
import com.walmartlabs.concord.client.ClientUtils;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.common.TemporaryPath;
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

    public void upload(UUID instanceId, Path payloadDir) throws Exception {
        Path attachmentsDir = payloadDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME);
        if (!Files.exists(attachmentsDir)) {
            return;
        }

        try (TemporaryPath tmp = IOUtils.tempFile("attachments", ".zip")) {
            try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(Files.newOutputStream(tmp.path()))) {
                IOUtils.zip(zip, attachmentsDir);
            }

            String path = "/api/v1/process/" + instanceId + "/attachment";

            ClientUtils.withRetry(AgentConstants.API_CALL_MAX_RETRIES, AgentConstants.API_CALL_RETRY_DELAY, () -> {
                ClientUtils.postData(apiClient, path, tmp.path().toFile());
                return null;
            });
        }
    }
}
