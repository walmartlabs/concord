package com.walmartlabs.concord.runtime.v2.runner;

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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.runtime.v2.sdk.EvalContext;
import com.walmartlabs.concord.runtime.v2.sdk.EvalContextFactory;
import com.walmartlabs.concord.runtime.v2.sdk.ExpressionEvaluator;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.svm.ExecutionListener;
import com.walmartlabs.concord.svm.Frame;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.State;

import javax.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Saves the process' {@code out} variables into a persistent file.
 * Normally, the server picks up the file and saves the data into the process' metadata.
 */
public class OutVariablesProcessor implements ExecutionListener {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final PersistenceService persistenceService;
    private final List<String> outVariables;

    @Inject
    public OutVariablesProcessor(ObjectMapper objectMapper, PersistenceService persistenceService, ProcessConfiguration processConfiguration) {
        this.objectMapper = objectMapper;
        this.persistenceService = persistenceService;
        this.outVariables = processConfiguration.out();
    }

    @Override
    public void afterProcessEnds(Runtime runtime, State state, Frame lastFrame) {
        if (outVariables.isEmpty() || lastFrame == null) {
            return;
        }

        Map<String, Object> vars = Collections.unmodifiableMap(lastFrame.getLocals());

        EvalContextFactory ecf = runtime.getService(EvalContextFactory.class);
        ExpressionEvaluator ee = runtime.getService(ExpressionEvaluator.class);
        EvalContext evalContext = EvalContext.builder()
                .from(ecf.strict(vars))
                .undefinedVariableAsNull(true)
                .resolveLazyValues(true)
                .build();

        Map<String, Object> outValues = new HashMap<>();
        for (String out : outVariables) {
            Object v = ee.eval(evalContext, "${" + out + "}", Object.class); // TODO sanitize

            if (v == null) {
                continue;
            }

            outValues.put(out, v);
        }

        if (outValues.isEmpty()) {
            return;
        }

        Map<String, Object> currentOut = persistenceService.loadPersistedFile(Constants.Files.OUT_VALUES_FILE_NAME,
                in -> objectMapper.readValue(in, MAP_TYPE));

        Map<String, Object> result = new HashMap<>(currentOut != null ? currentOut : Collections.emptyMap());
        result.putAll(outValues);

        persistenceService.persistFile(Constants.Files.OUT_VALUES_FILE_NAME,
                out -> objectMapper.writeValue(out, result));
    }
}
