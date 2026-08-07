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
import com.walmartlabs.concord.client2.ProcessApi;
import com.walmartlabs.concord.client2.ProcessEntry;
import com.walmartlabs.concord.client2.ProcessEventRequest;
import com.walmartlabs.concord.client2.ProcessEventsApi;
import com.walmartlabs.concord.runtime.common.SensitiveDataMasker;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.common.ObjectTruncater;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import com.walmartlabs.concord.runtime.v2.sdk.SensitiveDataHolder;
import com.walmartlabs.concord.runtime.v25.runner.engine.LifecycleEvent;
import com.walmartlabs.concord.runtime.v25.runner.engine.ProcessResult;
import com.walmartlabs.concord.runtime.v25.runner.engine.ProcessStatus;
import com.walmartlabs.concord.runtime.v25.runner.engine.StatusCallback;
import com.walmartlabs.concord.runtime.v25.runner.task.TaskRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

/** Delivers v2.5 lifecycle events through the server's v2 process-event contract. */
final class RunnerCallback implements StatusCallback, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RunnerCallback.class);

    private final RunnerConfiguration runnerConfiguration;
    private final ProcessConfiguration processConfiguration;
    private final SensitiveDataHolder sensitiveData;
    private final ProcessApi processApi;
    private final ProcessEventsApi eventsApi;
    private final RunnerLogging logging;
    private final int batchSize;
    private final ScheduledExecutorService flushScheduler;
    private final ThreadLocal<Long> activeLogSegment = new ThreadLocal<>();
    private static final int MAX_INVALID_EVENT_FILES = 100;

    private final List<ProcessEventRequest> pending = new ArrayList<>();
    private final Path invalidEventsDirectory;

    RunnerCallback(RunnerConfiguration runnerConfiguration, ProcessConfiguration processConfiguration,
                   ApiClient apiClient, SensitiveDataHolder sensitiveData, RunnerLogging logging) {
        this(runnerConfiguration, processConfiguration, apiClient, sensitiveData, logging,
                Path.of(System.getProperty("user.dir")));
    }

    RunnerCallback(RunnerConfiguration runnerConfiguration, ProcessConfiguration processConfiguration,
                   ApiClient apiClient, SensitiveDataHolder sensitiveData, RunnerLogging logging, Path workDirectory) {
        this.runnerConfiguration = runnerConfiguration;
        this.processConfiguration = processConfiguration;
        this.sensitiveData = sensitiveData;
        this.processApi = new ProcessApi(apiClient);
        this.eventsApi = new ProcessEventsApi(apiClient);
        this.logging = logging;
        this.invalidEventsDirectory = workDirectory.resolve(com.walmartlabs.concord.sdk.Constants.Files.JOB_ATTACHMENTS_DIR_NAME);
        this.batchSize = processConfiguration.events().batchSize();
        if (batchSize < 1) {
            throw new IllegalArgumentException("Process event batch size must be positive");
        }
        this.flushScheduler = Executors.newSingleThreadScheduledExecutor();
        flushScheduler.scheduleAtFixedRate(this::flush, processConfiguration.events().batchFlushInterval(),
                processConfiguration.events().batchFlushInterval(), TimeUnit.SECONDS);
    }

    void running() {
        updateStatus(ProcessEntry.StatusEnum.RUNNING);
    }

    @Override
    public synchronized void onEvent(LifecycleEvent event) {
        logging.onEvent(event);
        if (event.type() == LifecycleEvent.Type.STEP_STARTED) {
            var segment = logging.segment(event);
            if (segment == null) {
                activeLogSegment.remove();
            } else {
                activeLogSegment.set(segment);
            }
        }
        try {
            if (processConfiguration.events().recordEvents()) {
                pending.add(event(event));
                if (pending.size() >= batchSize) {
                    flush();
                }
            }
        } finally {
            if (event.type() == LifecycleEvent.Type.STEP_COMPLETED) {
                activeLogSegment.remove();
            }
        }
    }

    @Override
    public Long activeLogSegment() {
        return activeLogSegment.get();
    }

    @Override
    public synchronized void onTerminal(ProcessResult result) {
        if (result.status() == ProcessStatus.FAILED) {
            var failure = SensitiveDataMasker.mask(FailureRenderer.render(result.failure()), sensitiveData.get());
            pending.add(new ProcessEventRequest().eventType("ELEMENT")
                    .eventDate(OffsetDateTime.now(ZoneOffset.UTC))
                    .data(Map.of("description", "Error", "error", failure)));
            log.error("{}", failure);
        } else {
            log.info("Process {}", result.status().name().toLowerCase(java.util.Locale.ROOT));
        }
        flush();
    }

    synchronized void finish(ProcessResult result) {
        try {
            logging.finish(result.status());
        } finally {
            activeLogSegment.remove();
        }
    }

    V25LogEncoder.SegmentScope enterTask(TaskRuntime.Invocation invocation) {
        var step = invocation.step();
        return V25LogEncoder.scope(step != null && step.logSegment() != null
                ? step.logSegment()
                : step != null ? logging.segment(step) : null);
    }

    synchronized void onTask(TaskRuntime.Invocation invocation, Object result, Throwable failure) {
        if (!processConfiguration.events().recordEvents()) {
            return;
        }
        var data = taskEvent(invocation);
        if (processConfiguration.events().recordTaskInVars()) {
            putVariables(data, "in", taskVariables(invocation.arguments()), processConfiguration.events().inVarsBlacklist(),
                    processConfiguration.events().truncateInVars());
        }
        if (processConfiguration.events().recordTaskOutVars()) {
            putVariables(data, "out", taskVariables(result), processConfiguration.events().outVarsBlacklist(),
                    processConfiguration.events().truncateOutVars());
        }
        var step = invocation.step();
        if (processConfiguration.events().recordTaskMeta() && step != null) {
            putVariables(data, "meta", taskVariables(step.metadata().get("meta")),
                    processConfiguration.events().metaBlacklist(), processConfiguration.events().truncateMeta());
        }
        if (failure != null) {
            data.put("error", SensitiveDataMasker.mask(failure.toString(), sensitiveData.get()));
        }
        pending.add(new ProcessEventRequest().eventType("ELEMENT")
                .eventDate(OffsetDateTime.now(ZoneOffset.UTC))
                .data(data));
        if (pending.size() >= batchSize) {
            flush();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> taskVariables(Object value) {
        if (value instanceof Map<?, ?> values) {
            return new LinkedHashMap<>((Map<String, Object>) values);
        }
        if (value instanceof List<?> values && values.size() == 1 && values.get(0) instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        if (value instanceof com.walmartlabs.concord.runtime.v2.sdk.TaskResult.SimpleResult taskResult) {
            return taskResult.values();
        }
        return Map.of();
    }

    static Map<String, Object> taskEvent(TaskRuntime.Invocation invocation) {
        var data = new LinkedHashMap<String, Object>();
        data.put("description", "Task: " + invocation.taskName());
        data.put("name", invocation.taskName());
        if (!"execute".equals(invocation.methodName())) {
            data.put("method", invocation.methodName());
        }
        data.put("phase", "after");
        var step = invocation.step();
        if (step != null) {
            data.put("processDefinitionId", step.processDefinitionId());
            data.put("fileName", step.source());
            data.put("line", step.line());
            data.put("column", step.column());
            data.put("correlationId", correlationId(step.correlationId(), step.metadata()));
        }
        return data;
    }

    private void putVariables(Map<String, Object> event, String key, Map<String, Object> values,
                              Collection<String> blacklist, boolean truncate) {
        var filtered = new LinkedHashMap<String, Object>(values);
        blacklist.forEach(filtered::remove);
        filtered = new LinkedHashMap<>(SensitiveDataMasker.mask(filtered, sensitiveData.get()));
        if (truncate) {
            filtered = new LinkedHashMap<>(ObjectTruncater.truncateMap(filtered,
                    processConfiguration.events().truncateMaxStringLength(),
                    processConfiguration.events().truncateMaxArrayLength(),
                    processConfiguration.events().truncateMaxDepth()));
        }
        if (!filtered.isEmpty()) {
            event.put(key, filtered);
        }
    }

    @Override
    public synchronized void close() {
        flushScheduler.shutdown();
        flush();
    }

    private ProcessEventRequest event(LifecycleEvent event) {
        var data = new LinkedHashMap<String, Object>();
        data.put("processDefinitionId", event.path());
        data.put("fileName", event.source());
        data.put("line", event.line());
        data.put("column", event.column());
        data.put("description", event.type().name());
        data.put("correlationId", correlationId(event.correlationId(), event.data()));
        if (event.instructionId() != 0) {
            data.put("threadId", event.instructionId());
        }
        data.put("eventName", event.eventName());
        data.putAll(SensitiveDataMasker.mask(event.data(), sensitiveData.get()));
        return new ProcessEventRequest().eventType("ELEMENT")
                .eventDate(OffsetDateTime.now(ZoneOffset.UTC)).data(data);
    }

    static UUID correlationId(String value, Map<String, Object> metadata) {
        if (value == null) {
            return null;
        }
        var route = value + ":" + metadata.getOrDefault("loopItemIndex", 0) + ":"
                + metadata.getOrDefault("retryAttempt", 0);
        try {
            return UUID.fromString(route);
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(route.getBytes(StandardCharsets.UTF_8));
        }
    }

    private synchronized void flush() {
        if (pending.isEmpty()) {
            return;
        }
        var batch = List.copyOf(pending);
        pending.clear();
        try {
            ClientUtils.withRetry(runnerConfiguration.api().retryCount(), runnerConfiguration.api().retryInterval(), () -> {
                if (batch.size() == 1) {
                    eventsApi.event(processConfiguration.instanceId(), batch.get(0));
                } else {
                    eventsApi.batchEvent(processConfiguration.instanceId(), batch);
                }
                return null;
            });
        } catch (ApiException e) {
            saveUndeliverableEvents(batch);
            log.warn("Cannot deliver {} process event(s): {}", batch.size(), e.getMessage());
        }
    }

    private void saveUndeliverableEvents(List<ProcessEventRequest> batch) {
        try {
            var bytes = RunnerWiring.objectMapper().writeValueAsBytes(batch);
            var name = "invalid_event_" + hex(MessageDigest.getInstance("SHA-256").digest(bytes)) + ".json";
            Files.createDirectories(invalidEventsDirectory);
            var target = invalidEventsDirectory.resolve(name);
            if (Files.exists(target)) {
                return;
            }
            try (var files = Files.list(invalidEventsDirectory)) {
                if (files.filter(path -> path.getFileName().toString().startsWith("invalid_event_")).count()
                        >= MAX_INVALID_EVENT_FILES) {
                    log.warn("Cannot persist undeliverable process events: attachment limit {} reached",
                            MAX_INVALID_EVENT_FILES);
                    return;
                }
            }
            var temporary = Files.createTempFile(invalidEventsDirectory, name, ".tmp");
            try {
                Files.write(temporary, bytes);
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException | NoSuchAlgorithmException e) {
            log.warn("Cannot persist undeliverable process event batch", e);
        }
    }

    private static String hex(byte[] bytes) {
        var result = new StringBuilder(bytes.length * 2);
        for (var value : bytes) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }

    private void updateStatus(ProcessEntry.StatusEnum status) {
        try {
            ClientUtils.withRetry(runnerConfiguration.api().retryCount(), runnerConfiguration.api().retryInterval(), () -> {
                processApi.updateStatus(processConfiguration.instanceId(), runnerConfiguration.agentId(), status.toString());
                return null;
            });
        } catch (ApiException e) {
            throw new IllegalStateException("Cannot update process status to " + status, e);
        }
    }


    static final class FailureRenderer {
        static String render(ProcessResult.Failure failure) {
            var result = new StringBuilder(failure.code()).append(": ").append(failure.message());
            if (failure.source() != null) {
                result.append(System.lineSeparator()).append("  at ").append(failure.source())
                        .append(':').append(failure.line()).append(':').append(failure.column());
            }
            if (failure.path() != null) {
                result.append(System.lineSeparator()).append("  path: ").append(failure.path());
            }
            if (failure.parallelBranchIndex() != null) {
                result.append(System.lineSeparator()).append("  parallel branch: ").append(failure.parallelBranchIndex());
            }
            if (failure.loopItemIndex() != null) {
                result.append(System.lineSeparator()).append("  loop item: ").append(failure.loopItemIndex());
            }
            if (failure.retryAttempt() != null) {
                result.append(System.lineSeparator()).append("  retry attempt: ").append(failure.retryAttempt());
            }
            if (!failure.callStack().isEmpty()) {
                result.append(System.lineSeparator()).append("  flow stack: ").append(String.join(" -> ", failure.callStack()));
            }
            if (failure.cause() != null && !(failure.cause() instanceof com.walmartlabs.concord.runtime.v2.sdk.UserDefinedException)) {
                result.append(System.lineSeparator()).append("  cause: ").append(failure.cause());
            }
            return result.toString();
        }
    }
}
