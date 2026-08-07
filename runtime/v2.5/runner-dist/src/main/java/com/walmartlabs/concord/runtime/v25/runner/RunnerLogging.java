package com.walmartlabs.concord.runtime.v25.runner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import com.walmartlabs.concord.client2.ApiClient;
import com.walmartlabs.concord.client2.ApiException;
import com.walmartlabs.concord.client2.ClientUtils;
import com.walmartlabs.concord.client2.LogSegmentRequest;
import com.walmartlabs.concord.client2.ProcessLogV2Api;
import com.walmartlabs.concord.runtime.common.SensitiveDataMasker;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentStatus;
import com.walmartlabs.concord.runtime.v2.sdk.SensitiveDataHolder;
import com.walmartlabs.concord.runtime.v25.runner.engine.LifecycleEvent;
import com.walmartlabs.concord.runtime.v25.runner.engine.ProcessStatus;
import com.walmartlabs.concord.runtime.v25.runner.persistence.State25;
import com.walmartlabs.concord.runtime.v25.runner.task.TaskRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Creates a server log segment and configures stdout framing for the agent log collector. */
final class RunnerLogging {

    private static final Logger log = LoggerFactory.getLogger(RunnerLogging.class);

    private final RunnerConfiguration configuration;
    private final ProcessLogV2Api api;
    private final UUID instanceId;
    private final boolean segmented;
    private final SensitiveDataHolder sensitiveData;
    private Long systemSegment;
    private final Map<String, Long> stepSegments = new ConcurrentHashMap<>();
    private final Set<Long> activeStepSegments = ConcurrentHashMap.newKeySet();

    RunnerLogging(RunnerConfiguration configuration, ApiClient client, UUID instanceId,
                  SensitiveDataHolder sensitiveData, Path workDirectory) {
        this.configuration = configuration;
        this.api = new ProcessLogV2Api(client);
        this.instanceId = instanceId;
        this.segmented = configuration.logging().segmentedLogs();
        this.sensitiveData = sensitiveData;
        V25LogEncoder.segmented(segmented);
        V25LogLayout.configure(sensitiveData, configuration.logging().workDirMasking() ? workDirectory : null);
        if (configuration.logging().sendSystemOutAndErrToSLF4J()) {
            V25TaskOutput.install();
        }
    }

    void start() {
        if (!segmented) {
            return;
        }
        systemSegment = 0L;
        V25LogEncoder.segment(systemSegment);
    }

    void onEvent(LifecycleEvent event) {
        if (!segmented) {
            return;
        }
        if (event.type() == LifecycleEvent.Type.STEP_STARTED) {
            var name = SensitiveDataMasker.mask(segmentName(event), sensitiveData.get());
            if (name == null) {
                routeSystemSegment();
                return;
            }
            try {
                var request = new LogSegmentRequest()
                        .correlationId(RunnerCallback.correlationId(event.correlationId(), event.data())).name(name)
                        .createdAt(OffsetDateTime.now(ZoneOffset.UTC));
                var response = ClientUtils.withRetry(configuration.api().retryCount(), configuration.api().retryInterval(),
                        () -> api.createProcessLogSegment(instanceId, request));
                stepSegments.put(routeKey(event.correlationId(), event.data()), response.getId());
                activeStepSegments.add(response.getId());
                V25LogEncoder.segment(response.getId());
            } catch (ApiException e) {
                log.warn("Cannot create task log segment '{}': {}", name, e.getMessage());
                routeSystemSegment();
            }
        } else if (event.type() == LifecycleEvent.Type.STEP_COMPLETED) {
            var id = stepSegments.remove(routeKey(event.correlationId(), event.data()));
            if (id != null) {
                completeStepSegment(id, LogSegmentStatus.OK);
            }
            routeSystemSegment();
        }
    }

    private void completeStepSegment(long id, LogSegmentStatus status) {
        try {
            V25LogEncoder.finish(id, status);
        } finally {
            activeStepSegments.remove(id);
        }
    }

    private String segmentName(LifecycleEvent event) {
        var meta = event.data().get("meta");
        if (meta instanceof java.util.Map<?, ?> values) {
            var name = values.get("segmentName");
            if (name instanceof String value && !value.isBlank()) {
                return value;
            }
        }
        var name = event.data().get("name");
        return name instanceof String value && !value.isBlank() ? value : null;
    }

    Map<String, Long> snapshotSegments() {
        return segmented ? Map.copyOf(stepSegments) : Map.of();
    }

    State25 snapshot(State25 state) {
        return new State25(state.formatVersion(), state.planId(), state.entryPoint(), state.status(),
                state.terminalIntent(), state.checkpointName(), state.checkpointMetadata(), state.createdAtEpochMilli(),
                state.root(), state.waits(), state.history(), snapshotSegments());
    }

    void restoreSegments(Map<String, Long> segments) {
        if (!segmented || segments.isEmpty()) {
            return;
        }

        stepSegments.clear();
        activeStepSegments.clear();
        segments.forEach((route, id) -> {
            stepSegments.put(route, id);
            if (activeStepSegments.add(id)) {
                V25LogEncoder.finish(id, LogSegmentStatus.RUNNING);
            }
        });
        routeSystemSegment();
    }

    Long segment(TaskRuntime.StepContext step) {
        if (!segmented) {
            return null;
        }
        var id = stepSegments.get(routeKey(step.correlationId(), step.metadata()));
        return id != null ? id : systemSegment;
    }

    Long segment(LifecycleEvent event) {
        if (!segmented) {
            return null;
        }
        var id = stepSegments.get(routeKey(event.correlationId(), event.data()));
        return id != null ? id : systemSegment;
    }

    private static String routeKey(String correlationId, Map<String, Object> metadata) {
        return correlationId + ":" + metadata.getOrDefault("loopItemIndex", 0) + ":"
                + metadata.getOrDefault("retryAttempt", 0);
    }

    private void routeSystemSegment() {
        if (systemSegment != null) {
            V25LogEncoder.segment(systemSegment);
        } else {
            V25LogEncoder.clearSegment();
        }
    }

    void finish(ProcessStatus status) {
        var target = switch (status) {
            case SUCCEEDED -> LogSegmentStatus.OK;
            case FAILED, CANCELLED, TIMED_OUT -> LogSegmentStatus.ERROR;
            case SUSPENDED -> LogSegmentStatus.SUSPENDED;
            case RUNNING -> throw new IllegalArgumentException("RUNNING is not terminal");
        };
        stepSegments.clear();
        activeStepSegments.forEach(id -> completeStepSegment(id, target));
        if (systemSegment == null) {
            return;
        }
        V25LogEncoder.clearSegment();
        systemSegment = null;
    }
}
