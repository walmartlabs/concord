package com.walmartlabs.concord.runner.engine;

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
import com.walmartlabs.concord.project.InternalConstants;
import io.takari.bpm.EngineListener;
import io.takari.bpm.api.Variables;
import io.takari.bpm.state.ProcessInstance;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Map;

public class ProcessOutVariablesListener implements EngineListener {

    private final Path storeDir;
    private final ProcessOutVariables outVariables;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProcessOutVariablesListener(Path storeDir, ProcessOutVariables outVariables) {
        this.storeDir = storeDir;
        this.outVariables = outVariables;
    }

    @Override
    public ProcessInstance onFinalize(ProcessInstance state) {
        Map<String, Object> vars = outVariables.eval(state.getVariables());
        if (vars.isEmpty()) {
            return state;
        }

        try {
            if (!Files.exists(storeDir)) {
                Files.createDirectories(storeDir);
            }

            Path p = storeDir.resolve(InternalConstants.Files.OUT_VALUES_FILE_NAME);
            try (OutputStream out = Files.newOutputStream(p, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                objectMapper.writeValue(out, vars);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error while saving OUT variables", e);
        }

        return state;
    }
}
