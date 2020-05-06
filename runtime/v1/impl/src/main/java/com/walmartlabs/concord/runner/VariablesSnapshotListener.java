package com.walmartlabs.concord.runner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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
import io.takari.bpm.EngineListener;
import io.takari.bpm.api.Variables;
import io.takari.bpm.state.BpmnErrorHelper;
import io.takari.bpm.state.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Creates a snapshot of variables each time the process stops.
 * The snapshot can be used later to restore the variables in, for example, process forks,
 * onCancel handlers, etc.
 */
public class VariablesSnapshotListener implements EngineListener {

    private static final Logger log = LoggerFactory.getLogger(VariablesSnapshotListener.class);

    private final Path stateDir;

    public VariablesSnapshotListener(Path stateDir) {
        this.stateDir = stateDir;
    }

    @Override
    public ProcessInstance onFinalize(ProcessInstance state) {
        return processState(state);
    }

    @Override
    public void onUnhandledException(ProcessInstance state) {
        processState(state);
    }

    private ProcessInstance processState(ProcessInstance state) {
        Variables vars = state.getVariables();

        // remove some internal variables before saving
        vars = BpmnErrorHelper.clear(vars);

        try {
            Path dst = stateDir.resolve(InternalConstants.Files.LAST_KNOWN_VARIABLES_FILE_NAME);
            try (OutputStream out = Files.newOutputStream(dst, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                SerializationUtils.serialize(out, vars);
            }
        } catch (IOException e) {
            log.error("Can't save a snapshot of the process variables. Process forks (including onError, onCancel and " +
                    "other handlers) may not receive the updated variables. Error: {}", e.getMessage());
        }

        return state;
    }
}
