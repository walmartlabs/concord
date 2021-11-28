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

import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.client.ClientUtils;
import com.walmartlabs.concord.client.ProcessApi;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.common.injector.InstanceId;
import com.walmartlabs.concord.runtime.v2.model.Location;
import com.walmartlabs.concord.runtime.v2.runner.vm.StepCommand;
import com.walmartlabs.concord.runtime.v2.runner.vm.VMUtils;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Records updates to the process' metadata.
 *
 * @see ProcessConfiguration#meta()
 */
public class MetadataProcessor implements ExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(MetadataProcessor.class);

    private static final Set<Class<?>> VARIABLE_TYPES = new HashSet<>(Arrays.asList(
            Boolean.class,
            Byte.class,
            Character.class,
            Double.class,
            Float.class,
            Integer.class,
            Long.class,
            Short.class,
            String.class));

    private final InstanceId processInstanceId;
    private final Set<String> metaVariables;
    private final ProcessApi processApi;
    private final int retryCount;
    private final int retryInterval;

    private final AtomicReference<Map<String, Object>> latestMeta = new AtomicReference<>(new HashMap<>());
    private final AtomicReference<Set<String>> latestInvalidMeta = new AtomicReference<>(new HashSet<>());

    @Inject
    public MetadataProcessor(InstanceId processInstanceId,
                             ProcessConfiguration processConfiguration,
                             RunnerConfiguration runnerConfiguration,
                             ApiClient apiClient) {

        this.processInstanceId = processInstanceId;
        this.metaVariables = processConfiguration.meta().keySet();
        this.processApi = new ProcessApi(apiClient);
        this.retryCount = runnerConfiguration.api().retryCount();
        this.retryInterval = runnerConfiguration.api().retryInterval();
    }

    @Override
    public Result afterCommand(Runtime runtime, VM vm, State state, ThreadId threadId, Command cmd) {
        if (!(cmd instanceof StepCommand)) {
            return Result.CONTINUE;
        }

        if (metaVariables.isEmpty()) {
            return Result.CONTINUE;
        }

        Map<String, Object> currentProcessVariables = VMUtils.getCombinedLocals(state, threadId);

        Map<String, Object> metaValues = varsToValues(metaVariables, currentProcessVariables);

        Map<String, Object> invalidValues = new HashMap<>();
        Map<String, Object> cleanValues = cleanupValues(metaValues, invalidValues);

        Set<String> newInvalid = new HashSet<>(latestInvalidMeta.get());
        newInvalid.addAll(invalidValues.keySet());
        newInvalid.removeAll(cleanValues.keySet());

        invalidValues.keySet().removeAll(latestInvalidMeta.get());
        invalidValues.forEach((k, v) -> log.info("meta variable '{}' with value '{}' -> ignored (unsupported type: {}). {}", k, v, v.getClass(),
                Location.toErrorPrefix(((StepCommand<?>) cmd).getStep().getLocation())));

        latestInvalidMeta.set(newInvalid);

        if (cleanValues.isEmpty() || !changed(latestMeta.get(), cleanValues)) {
            return Result.CONTINUE;
        }

        latestMeta.set(cleanValues);

        try {
            ClientUtils.withRetry(retryCount, retryInterval, () -> {
                processApi.updateMetadata(processInstanceId.getValue(), cleanValues);
                return null;
            });
            return Result.CONTINUE;
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, Object> varsToValues(Set<String> metaVariables, Map<String, Object> vars) {
        return metaVariables.stream()
                .map(v -> {
                    Object variableValue = ConfigurationUtils.get(vars, v.split("\\."));
                    if (variableValue == null) {
                        return null;
                    }
                    return new AbstractMap.SimpleEntry<>(v, variableValue);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static Map<String, Object> cleanupValues(Map<String, Object> values, Map<String, Object> invalidValuesAccumulator) {
        Map<String, Object> result = new HashMap<>(values.size());
        for (Map.Entry<String, Object> e : values.entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            v = unwind(v);
            if (v.getClass().isPrimitive() || VARIABLE_TYPES.contains(v.getClass())) {
                result.put(k, v);
            } else {
                invalidValuesAccumulator.put(k, v);
            }
        }
        return result;
    }

    private static boolean changed(Map<String, Object> oldMeta, Map<String, Object> newMeta) {
        return !oldMeta.equals(newMeta);
    }

    @SuppressWarnings("unchecked")
    private static Object unwind(Object value) {
        if (!(value instanceof List)) {
            return value;
        }

        List<Object> v = (List<Object>) value;

        if (v.isEmpty()) {
            return null;
        }

        return unwind(v.get(v.size() - 1));
    }
}
