package com.walmartlabs.concord.policyengine;

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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class PolicyEngine {

    private static final ObjectMapper objectMapper = createObjectMapper();

    private final DependencyPolicy dependencyPolicy;
    private final FilePolicy filePolicy;
    private final TaskPolicy taskPolicy;
    private final WorkspacePolicy workspacePolicy;
    private final ContainerPolicy containerPolicy;
    private final QueueProcessPolicy queueProcessPolicy;
    private final ConcurrentProcessPolicy concurrentProcessPolicy;

    public PolicyEngine(Map<String, Object> rules) {
        this(objectMapper.convertValue(rules, PolicyEngineRules.class));
    }

    public PolicyEngine(PolicyEngineRules rules) {
        this.dependencyPolicy = new DependencyPolicy(rules.getDependencyRules());
        this.filePolicy = new FilePolicy(rules.getFileRules());
        this.taskPolicy = new TaskPolicy(rules.getTaskRules());
        this.workspacePolicy = new WorkspacePolicy(rules.getWorkspaceRule());
        this.containerPolicy = new ContainerPolicy(rules.getContainerRules());

        QueueRule qr = rules.getQueueRules();
        if (qr == null) {
            qr = new QueueRule(null, null, null, null );
        }
        this.queueProcessPolicy = new QueueProcessPolicy(qr.getProcess(), qr.getProcessPerOrg(), qr.getProcessPerProject());
        this.concurrentProcessPolicy = new ConcurrentProcessPolicy(qr.getConcurrent());
    }

    public DependencyPolicy getDependencyPolicy() {
        return dependencyPolicy;
    }

    public FilePolicy getFilePolicy() {
        return filePolicy;
    }

    public TaskPolicy getTaskPolicy() {
        return taskPolicy;
    }

    public WorkspacePolicy getWorkspacePolicy() {
        return workspacePolicy;
    }

    public ContainerPolicy getContainerPolicy() {
        return containerPolicy;
    }

    public QueueProcessPolicy getQueueProcessPolicy() {
        return queueProcessPolicy;
    }

    public ConcurrentProcessPolicy getConcurrentProcessPolicy() {
        return concurrentProcessPolicy;
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper om = new ObjectMapper();
        om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return om;
    }
}
