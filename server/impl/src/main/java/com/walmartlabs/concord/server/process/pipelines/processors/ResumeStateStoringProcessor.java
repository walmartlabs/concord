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

import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import com.walmartlabs.concord.server.sdk.ProcessKey;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Named
public class ResumeStateStoringProcessor implements PayloadProcessor {


    private final ProcessLogManager logManager;

    @Inject
    public ResumeStateStoringProcessor(ProcessLogManager logManager) {
        this.logManager = logManager;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        ProcessKey processKey = payload.getProcessKey();

        String eventName = payload.getHeader(Payload.EVENT_NAME);
        if (eventName == null) {
            return chain.process(payload);
        }

        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);
        Path stateDir = workspace.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(Constants.Files.JOB_STATE_DIR_NAME);

        try {
            if (!Files.exists(stateDir)) {
                Files.createDirectories(stateDir);
            }

            Path resumeMarker = stateDir.resolve(Constants.Files.RESUME_MARKER_FILE_NAME);
            Files.write(resumeMarker, eventName.getBytes());
        } catch (IOException e) {
            logManager.error(processKey, "Error while saving resume event", e);
            throw new ProcessException(processKey, "Error while saving resume event", e);
        }

        return chain.process(payload);
    }
}
