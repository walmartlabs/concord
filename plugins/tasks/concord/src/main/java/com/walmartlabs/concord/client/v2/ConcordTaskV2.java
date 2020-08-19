package com.walmartlabs.concord.client.v2;

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

import com.walmartlabs.concord.client.*;
import com.walmartlabs.concord.runtime.v2.model.ProjectInfo;
import com.walmartlabs.concord.runtime.v2.sdk.*;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.client.ConcordTaskParams.KillParams;
import static com.walmartlabs.concord.client.ConcordTaskParams.ListSubProcesses;

@Named("concord")
public class ConcordTaskV2 implements ReentrantTask {

    private final Variables defaults;
    private final String sessionToken;
    private final UUID instanceId;
    private final ApiClientFactory apiClientFactory;
    private final ProjectInfo projectInfo;
    private final Path workDir;
    private final ConcordTaskSuspender suspender;

    @Inject
    public ConcordTaskV2(ApiClientFactory apiClientFactory, Context context) {
        this.defaults = context.defaultVariables();
        this.sessionToken = context.processConfiguration().processInfo().sessionToken();
        this.instanceId = context.processInstanceId();
        this.apiClientFactory = apiClientFactory;

        ProjectInfo projectInfo = context.projectInfo();
        if (projectInfo == null) {
            projectInfo = ProjectInfo.builder().build();
        }
        this.projectInfo = projectInfo;

        this.workDir = context.workingDirectory();
        this.suspender = (resumeFromSameStep, payload) -> {
            if (resumeFromSameStep) {
                return context.suspendResume(payload.asMap());
            } else {
                String eventName = UUID.randomUUID().toString();
                context.suspend(eventName);
                return eventName;
            }
        };
    }

    @Override
    public TaskResult execute(Variables in) throws Exception {
        return delegate().execute(ConcordTaskParams.of(in));
    }

    @Override
    public TaskResult resume(ResumeEvent event) throws Exception {
        return delegate().continueAfterSuspend(new ConcordTaskSuspender.ResumePayload(event.state()));
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
        delegate().suspendForCompletion(ids.stream()
                .map(UUID::fromString)
                .collect(Collectors.toList()));
    }

    public Map<String, ProcessEntry> waitForCompletion(List<String> ids) {
        return waitForCompletion(ids, -1);
    }

    public Map<String, ProcessEntry> waitForCompletion(List<String> ids, long timeout) {
        return waitForCompletion(ids, timeout, Function.identity());
    }

    public <T> Map<String, T> waitForCompletion(List<String> ids, long timeout, Function<ProcessEntry, T> processor) {
        return delegate().waitForCompletion(toUUIDs(ids), timeout, processor);
    }

    public void kill(Map<String, Object> cfg) throws Exception {
        delegate().kill(new KillParams(new MapBackedVariables(cfg)));
    }

    public Map<String, Map<String, Object>> getOutVars(List<String> ids) {
        return getOutVars(ids, -1);
    }

    public Map<String, Map<String, Object>> getOutVars(List<String> ids, long timeout) {
        return delegate().getOutVars(null, null, toUUIDs(ids), timeout);
    }

    private ConcordTaskCommon delegate() {
        return new ConcordTaskCommon(sessionToken, apiClientFactory, defaults.getString("processLinkTemplate"), instanceId, projectInfo.orgName(), workDir, suspender);
    }

    private static List<UUID> toUUIDs(List<String> ids) {
        return ids.stream().map(UUID::fromString).collect(Collectors.toList());
    }
}
