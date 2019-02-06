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

import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;

import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Cleans up the fork's files copied from the parent process.
 */
@Named
public class ForkCleanupProcessor implements PayloadProcessor {

    private static final String[] MARKER_FILES = {InternalConstants.Files.SUSPEND_MARKER_FILE_NAME, InternalConstants.Files.RESUME_MARKER_FILE_NAME};

    @Override
    public Payload process(Chain chain, Payload payload) {
        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);
        Path stateDir = workspace.resolve(InternalConstants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(InternalConstants.Files.JOB_STATE_DIR_NAME);

        try {
            // clear the parent process' suspend/resume markers
            // otherwise the fork could try to resume a parent process' event
            for (String m : MARKER_FILES) {
                Path suspendMarker = stateDir.resolve(m);
                Files.deleteIfExists(suspendMarker);
            }

            // remove the parent process' arguments file if a state snapshot is present
            // we don't want the original process arguments to overwrite the process variables
            Path stateSnapshot = stateDir.resolve(InternalConstants.Files.LAST_KNOWN_VARIABLES_FILE_NAME);
            if (Files.exists(stateSnapshot)) {
                Path requestFile = workspace.resolve(InternalConstants.Files.REQUEST_DATA_FILE_NAME);
                Files.deleteIfExists(requestFile);
            }
        } catch (IOException e) {
            throw new ProcessException(payload.getProcessKey(), "Error while preparing the fork's data", e);
        }

        return chain.process(payload);
    }
}
