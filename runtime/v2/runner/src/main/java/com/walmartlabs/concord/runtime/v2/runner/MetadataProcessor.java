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

import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.common.injector.InstanceId;
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

    private final AtomicReference<Map<String, Object>> currentMeta = new AtomicReference<>(new HashMap<>());

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

        Map<String, Object> vars = VMUtils.getCombinedLocals(state, threadId);

        Map<String, Object> meta = filter(vars, metaVariables);
        if (meta.isEmpty() || !changed(currentMeta.get(), meta)) {
            return Result.CONTINUE;
        }

        currentMeta.set(meta);

        try {
            ClientUtils.withRetry(retryCount, retryInterval, () -> {
                processApi.updateMetadata(processInstanceId.getValue(), meta);
                return null;
            });
            return Result.CONTINUE;
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, Object> filter(Map<String, Object> vars, Set<String> metaVariables) {
        if (vars.isEmpty()) {
            return vars;
        }

        Map<String, Object> result = new HashMap<>();
        for (String v : metaVariables) {
            Object value = ConfigurationUtils.get(vars, v.split("\\."));
            if (value == null) {
                continue;
            }

            if (value.getClass().isPrimitive() || VARIABLE_TYPES.contains(value.getClass())) {
                result.put(v, value);
            } else {
                log.debug("meta variable '{}' -> ignored (unsupported type: {})", v, value.getClass());
            }
        }

        return result;
    }

    private static boolean changed(Map<String, Object> oldMeta, Map<String, Object> newMeta) {
        return !oldMeta.equals(newMeta);
    }
}
