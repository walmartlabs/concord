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

import com.walmartlabs.concord.client2.ApiException;
import com.walmartlabs.concord.client2.ApiClientConfiguration;
import com.walmartlabs.concord.client2.ApiClientFactory;
import com.walmartlabs.concord.client2.ClientUtils;
import com.walmartlabs.concord.client2.ProcessApi;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.runner.ContextUtils;
import io.takari.bpm.api.Variables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ProcessMetadataProcessor {

    private static final Logger log = LoggerFactory.getLogger(ProcessMetadataProcessor.class);

    private static final int RETRY_COUNT = 3;
    private static final long RETRY_INTERVAL = 5000;

    @SuppressWarnings("rawtypes")
    private static final Set<Class> VARIABLE_TYPES = new HashSet<>(Arrays.asList(
            String.class, Boolean.class, Character.class, Byte.class, Short.class, Integer.class, Long.class, Float.class, Double.class));

    private final ApiClientFactory apiClientFactory;
    private final Set<String> processMetaVariables;

    private Map<String, Object> currentProcessMeta = new HashMap<>();

    public ProcessMetadataProcessor(ApiClientFactory apiClientFactory, Set<String> processMetaVariables) {
        this.apiClientFactory = apiClientFactory;
        this.processMetaVariables = processMetaVariables;
    }

    public void process(UUID instanceId, Variables variables) {
        Map<String, Object> meta = filter(variables.asMap());
        if (meta.isEmpty() || !changed(currentProcessMeta, meta)) {
            return;
        }
        currentProcessMeta = meta;

        ProcessApi client = new ProcessApi(apiClientFactory.create(
                ApiClientConfiguration.builder()
                        .sessionToken(ContextUtils.getSessionToken(variables))
                        .build()));
        try {
            ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL, () -> {
                client.updateMetadata(instanceId, meta);
                return null;
            });
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean changed(Map<String, Object> oldMeta, Map<String, Object> meta) {
        return !oldMeta.equals(meta);
    }

    private Map<String, Object> filter(Map<String, Object> vars) {
        if (vars.isEmpty()) {
            return vars;
        }

        Map<String, Object> result = new HashMap<>();
        for (String v : processMetaVariables) {
            Object value = ConfigurationUtils.get(vars, v.split("\\."));
            if (value == null) {
                continue;
            }

            if (value.getClass().isPrimitive() || VARIABLE_TYPES.contains(value.getClass())) {
                result.put(v, value);
            } else {
                log.debug("out variable {} -> ignored (unsupported type: {})", v, value.getClass());
            }
        }
        return result;
    }
}
