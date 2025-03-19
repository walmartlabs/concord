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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

/**
 * Cleans up the fork's files copied from the parent process.
 */
public class ForkCleanupProcessor implements PayloadProcessor {

    private static final String[] MARKER_FILES = {Constants.Files.SUSPEND_MARKER_FILE_NAME, Constants.Files.RESUME_MARKER_FILE_NAME};

    private final ObjectMapper objectMapper;

    @Inject
    public ForkCleanupProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);
        Path stateDir = workspace.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(Constants.Files.JOB_STATE_DIR_NAME);

        try {
            // clear the parent process' suspend/resume markers
            // otherwise the fork could try to resume a parent process' event
            for (String m : MARKER_FILES) {
                Path suspendMarker = stateDir.resolve(m);
                Files.deleteIfExists(suspendMarker);
            }

            // remove the parent process' arguments file if a state snapshot is present
            // we don't want the original process arguments to overwrite the process variables
            Path stateSnapshot = stateDir.resolve(Constants.Files.LAST_KNOWN_VARIABLES_FILE_NAME);
            if (Files.exists(stateSnapshot)) {
                clearArguments(workspace);
            }
        } catch (IOException e) {
            throw new ProcessException(payload.getProcessKey(), "Error while preparing the fork's data", e);
        }

        return chain.process(payload);
    }

    @SuppressWarnings("unchecked")
    private void clearArguments(Path workspace) throws IOException {
        Path requestFile = workspace.resolve(Constants.Files.CONFIGURATION_FILE_NAME);
        if (!Files.exists(requestFile)) {
            return;
        }

        Map<String, Object> m;
        try (InputStream in = Files.newInputStream(requestFile)) {
            m = objectMapper.readValue(in, Map.class);
        }

        if (m == null || m.isEmpty()) {
            return;
        }

        m.remove(Constants.Request.ARGUMENTS_KEY);

        try (OutputStream out = Files.newOutputStream(requestFile, StandardOpenOption.TRUNCATE_EXISTING)) {
            objectMapper.writeValue(out, m);
        }
    }
}
