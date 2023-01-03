package com.walmartlabs.concord.runner.engine;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import com.walmartlabs.concord.sdk.Context;
import io.takari.bpm.api.ExecutionException;

import javax.el.ExpressionFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class PolicyPreprocessor implements TaskInterceptor {

    private final ExpressionFactory expressionFactory = ExpressionFactory.newInstance();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Path workDir;
    private final Map<String, Object> policy;

    public PolicyPreprocessor(Path workDir) {
        this.workDir = workDir;
        this.policy = readPolicy(workDir);
    }

    @Override
    public void preTask(String taskName, Object instance, Context ctx) throws ExecutionException {
        if (!needProcess(taskName)) {
            return;
        }

        Map<String, Object> newPolicy = eval(ctx, policy);
        writePolicy(newPolicy);
    }

    @Override
    public void postTask(String taskName, Object instance, Context ctx) throws ExecutionException {
        if (!needProcess(taskName)) {
            return;
        }

        writePolicy(policy);
    }

    private boolean needProcess(String taskName) {
        return taskName.startsWith("ansible") && !policy.isEmpty();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readPolicy(Path workDir) {
        Path policyFile = workDir.resolve(InternalConstants.Files.CONCORD_SYSTEM_DIR_NAME).resolve(InternalConstants.Files.POLICY_FILE_NAME);
        if (!Files.exists(policyFile)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(policyFile.toFile(), Map.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void writePolicy(Map<String, Object> policy) throws ExecutionException {
        try {
            Path policyFile = workDir.resolve(InternalConstants.Files.CONCORD_SYSTEM_DIR_NAME).resolve(InternalConstants.Files.POLICY_FILE_NAME);
            objectMapper.writeValue(policyFile.toFile(), policy);
        } catch (IOException e) {
            throw new ExecutionException("write policy error", e);
        }
    }

    private Map<String, Object> eval(Context ctx, Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> e : params.entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            result.put(k, process(ctx, v));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Object process(Context ctx, Object v) {
        if (v instanceof String) {
            return eval(ctx, (String) v);
        } else if (v instanceof Map) {
            Map<String, Object> result = new HashMap<>();
            for (Map.Entry<String, Object> e : ((Map<String, Object>)v).entrySet()) {
                result.put(e.getKey(), process(ctx, e.getValue()));
            }
            return result;
        } else if (v instanceof List) {
            List<Object> result = new ArrayList<>();
            for (Object o : (List)v) {
                result.add(process(ctx, o));
            }
            return result;
        } else {
            return v;
        }
    }

    private Object eval(Context ctx, String expression) {
        if (!isExpression(expression)) {
            return expression;
        }

        return ctx.eval(expression, Object.class);
    }

    private boolean isExpression(String str) {
        if (str == null) {
            return false;
        }

        String s = str.trim();
        return s.startsWith("${") && s.endsWith("}");
    }
}
