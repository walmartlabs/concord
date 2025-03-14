package com.walmartlabs.concord.client.v2;

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

import com.walmartlabs.concord.client.*;
import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.runtime.v2.sdk.*;
import com.walmartlabs.concord.sdk.LogTags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.client.ConcordTaskParams.KillParams;
import static com.walmartlabs.concord.client.ConcordTaskParams.ListSubProcesses;

@Named("concord")
@DryRunReady
@SuppressWarnings("unused")
public class ConcordTaskV2 implements ReentrantTask {

    private static final Logger log = LoggerFactory.getLogger(ConcordTaskV2.class);

    private final ApiClientFactory apiClientFactory;
    private final Context context;

    @Inject
    public ConcordTaskV2(ApiClientFactory apiClientFactory, Context context) {
        this.apiClientFactory = apiClientFactory;
        this.context = context;
    }

    @Override
    public TaskResult execute(Variables in) throws Exception {
        return delegate().execute(ConcordTaskParams.of(in, context.defaultVariables().toMap()));
    }

    @Override
    public TaskResult resume(ResumeEvent event) throws Exception {
        return delegate().continueAfterSuspend(new ResumePayload(event.state()));
    }

    public List<String> listSubprocesses(String instanceId, String... tags) throws Exception {
        return delegate().listSubProcesses(ListSubProcesses.of(UUID.fromString(instanceId), tags)).stream()
                .map(e -> e.getInstanceId().toString())
                .collect(Collectors.toList());
    }

    public List<String> listSubprocesses(Map<String, Object> cfg) throws Exception {
        return delegate().listSubProcesses(new ListSubProcesses(new MapBackedVariables(cfg))).stream()
                .map(e -> e.getInstanceId().toString())
                .collect(Collectors.toList());
    }

    public void suspendForCompletion(List<String> ids) throws Exception {
        if (ids.isEmpty()) {
            return;
        }

        String eventName = delegate().suspendForCompletion(ids.stream()
                .map(UUID::fromString)
                .collect(Collectors.toList()));
        context.suspend(eventName);
    }

    public Map<String, ProcessEntry> waitForCompletion(String id) {
        return waitForCompletion(Collections.singletonList(id));
    }

    public Map<String, ProcessEntry> waitForCompletion(List<String> ids) {
        return waitForCompletion(ids, -1);
    }

    public Map<String, ProcessEntry> waitForCompletion(String id, long timeout) {
        return waitForCompletion(Collections.singletonList(id), timeout);
    }

    public Map<String, ProcessEntry> waitForCompletion(List<String> ids, long timeout) {
        return waitForCompletion(ids, timeout, Function.identity());
    }

    public <T> Map<String, T> waitForCompletion(List<String> ids, long timeout, Function<ProcessEntry, T> processor) {
        return delegate().waitForCompletion(toUUIDs(ids), timeout, processor);
    }

    public void assertFinished(List<String> ids) {
        var result = waitForCompletion(ids);
        boolean hasFailed = false;
        for (var p : result.values()) {
            if (p.getStatus() == ProcessEntry.StatusEnum.FAILED) {
                log.info("Failed process: {}", LogTags.instanceId(p.getInstanceId()));
                hasFailed = true;
            }
        }

        if (hasFailed) {
            throw new UserDefinedException("Found failed processes");
        }
    }

    public void kill(Map<String, Object> cfg) throws Exception {
        delegate().kill(new KillParams(new MapBackedVariables(cfg)));
    }

    public Map<String, Map<String, Object>> getOutVars(String id) {
        return getOutVars(Collections.singletonList(id));
    }

    public Map<String, Map<String, Object>> getOutVars(List<String> ids) {
        return getOutVars(ids, -1);
    }

    public Map<String, Map<String, Object>> getOutVars(String id, long timeout) {
        return getOutVars(Collections.singletonList(id), timeout);
    }

    public Map<String, Map<String, Object>> getOutVars(List<String> ids, long timeout) {
        return delegate().getOutVars(null, null, toUUIDs(ids), timeout);
    }

    public void suspend(String eventId) {
        this.context.suspend(eventId);
    }

    public void resume(String processId, String eventId, String saveAs, Map<String, Object> payload) throws ApiException {
        ClientUtils.withRetry(3, 15000, () -> { // TODO: input args?
            var api = new ProcessApi(apiClientFactory.create(ApiClientConfiguration.builder().build()));
            api.resume(UUID.fromString(processId), eventId, saveAs, payload);
            return null;
        });
    }

    private ConcordTaskCommon delegate() {
        String sessionToken = context.processConfiguration().processInfo().sessionToken();
        UUID instanceId = context.processInstanceId();
        ProjectInfo projectInfo = context.processConfiguration().projectInfo();
        return new ConcordTaskCommon(sessionToken, apiClientFactory, instanceId, projectInfo.orgName(),
                context.workingDirectory(), context.processConfiguration().debug(), context.processConfiguration().dryRun());
    }

    private static List<UUID> toUUIDs(List<String> ids) {
        return ids.stream().map(UUID::fromString).collect(Collectors.toList());
    }
}
