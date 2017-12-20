package com.walmartlabs.concord.runner.engine;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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
import io.takari.bpm.api.ExecutionContext;
import io.takari.bpm.context.ExecutionContextFactory;
import io.takari.bpm.context.ExecutionContextImpl;
import io.takari.bpm.state.ProcessInstance;
import io.takari.bpm.state.Variables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.el.PropertyNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ProcessOutVariablesListener implements EngineListener {

    private static final Logger log = LoggerFactory.getLogger(ProcessOutVariablesListener.class);

    private final ExecutionContextFactory<? extends ExecutionContextImpl> contextFactory;
    private final Path storeDir;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProcessOutVariablesListener(ExecutionContextFactory<? extends ExecutionContextImpl> contextFactory, Path storeDir) {
        this.contextFactory = contextFactory;
        this.storeDir = storeDir;
    }

    @Override
    public ProcessInstance onFinalize(ProcessInstance state) {
        Collection<String> outExprs = getOutExpressions(state);
        if (outExprs == null || outExprs.isEmpty()) {
            return state;
        }

        Map<String, Object> result = new HashMap<>();
        for (String x : outExprs) {
            Variables vars = state.getVariables();
            ExecutionContext ctx = contextFactory.create(vars);

            Object v;
            try {
                v = ctx.eval("${" + x + "}", Object.class);
            } catch (PropertyNotFoundException e) {
                log.warn("OUT variable not found: {}", x);
                v = null;
            }

            if (v == null) {
                continue;
            }

            result.put(x, v);
        }

        try {
            if (!Files.exists(storeDir)) {
                Files.createDirectories(storeDir);
            }

            Path p = storeDir.resolve(InternalConstants.Files.OUT_VALUES_FILE_NAME);
            try (OutputStream out = Files.newOutputStream(p, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                objectMapper.writeValue(out, result);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error while saving OUT variables", e);
        }

        return state;
    }

    private static Collection<String> getOutExpressions(ProcessInstance state) {
        Variables vars = state.getVariables();

        Object o = vars.getVariable(InternalConstants.Context.OUT_EXPRESSIONS_KEY);
        if (o == null) {
            return null;
        }

        if (!(o instanceof Collection)) {
            throw new IllegalArgumentException("Invalid type of OUT value expression list: " + o.getClass());
        }

        return (Collection<String>) o;
    }
}
