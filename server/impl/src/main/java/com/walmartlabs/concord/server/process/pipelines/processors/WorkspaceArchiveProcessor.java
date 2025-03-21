package com.walmartlabs.concord.server.process.pipelines.processors;

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

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import com.walmartlabs.concord.server.sdk.ProcessKey;

import javax.inject.Inject;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Unpacks payload's workspace file, parses request data.
 */
public class WorkspaceArchiveProcessor implements PayloadProcessor {

    private final ProcessLogManager logManager;

    @Inject
    public WorkspaceArchiveProcessor(ProcessLogManager logManager) {
        this.logManager = logManager;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        ProcessKey processKey = payload.getProcessKey();

        Path archive = payload.getAttachment(Payload.WORKSPACE_ARCHIVE);
        if (archive == null) {
            return chain.process(payload);
        }

        if (!Files.exists(archive)) {
            logManager.error(processKey, "No input archive found: " + archive);
            throw new ProcessException(processKey, "No input archive found: " + archive, Status.BAD_REQUEST);
        }

        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);
        try {
            IOUtils.unzip(archive, workspace, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logManager.error(processKey, "Error while unpacking an archive: " + archive, e);
            throw new ProcessException(processKey, "Error while unpacking an archive: " + archive, e);
        }

        payload = payload.removeAttachment(Payload.WORKSPACE_ARCHIVE);
        return chain.process(payload);
    }
}
