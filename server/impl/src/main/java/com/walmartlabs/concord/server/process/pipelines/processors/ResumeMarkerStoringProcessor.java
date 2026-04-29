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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;

/**
 * Adds an event marker to the payload.
 * <p>
 * The event marker file is used by the runtime to determine whether the process
 * should be started using a previously saved state snapshot.
 */
public class ResumeMarkerStoringProcessor implements PayloadProcessor {

    private final ProcessLogManager logManager;

    @Inject
    public ResumeMarkerStoringProcessor(ProcessLogManager logManager) {
        this.logManager = logManager;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        ProcessKey processKey = payload.getProcessKey();

        Set<String> events = payload.getHeader(Payload.RESUME_EVENTS, Collections.emptySet());
        if (events.isEmpty()) {
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
            Files.write(resumeMarker, events);
        } catch (IOException e) {
            logManager.error(processKey, "Error while saving resume event", e);
            throw new ProcessException(processKey, "Error while saving resume event", e);
        }

        return chain.process(payload);
    }
}
