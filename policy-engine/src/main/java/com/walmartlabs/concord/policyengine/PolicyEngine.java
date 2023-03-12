package com.walmartlabs.concord.policyengine;

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

import java.util.Collections;
import java.util.List;

public class PolicyEngine {

    private final List<String> ruleNames;
    private final PolicyEngineRules rules;

    private final DependencyPolicy dependencyPolicy;
    private final DependencyRewritePolicy dependencyRewritePolicy;
    private final FilePolicy filePolicy;
    private final TaskPolicy taskPolicy;
    private final WorkspacePolicy workspacePolicy;
    private final AttachmentsPolicy attachmentsPolicy;
    private final ContainerPolicy containerPolicy;
    private final ConcurrentProcessPolicy concurrentProcessPolicy;
    private final ForkDepthPolicy forkDepthPolicy;
    private final ProcessTimeoutPolicy processTimeoutPolicy;
    private final ProtectedTasksPolicy protectedTasksPolicy;
    private final EntityPolicy entityPolicy;
    private final ProcessCfgPolicy processCfgPolicy;
    private final JsonStorePolicy jsonStoragePolicy;
    private final ProcessCfgPolicy defaultProcessCfgPolicy;
    private final DependencyVersionsPolicy defaultDependencyVersionsPolicy;
    private final StatePolicy statePolicy;
    private final RawPayloadPolicy rawPayloadPolicy;
    private final RuntimePolicy runtimePolicy;
    private final CronTriggerPolicy cronTriggerPolicy;
    private final KvPolicy kvPolicy;

    public PolicyEngine(PolicyEngineRules rules) {
        this(Collections.emptyList(), rules);
    }

    public PolicyEngine(String ruleName, PolicyEngineRules rules) {
        this(Collections.singletonList(ruleName), rules);
    }

    public PolicyEngine(List<String> ruleNames, PolicyEngineRules rules) {
        this.ruleNames = ruleNames;
        this.rules = rules;
        this.dependencyPolicy = new DependencyPolicy(rules.dependencyRules());
        this.dependencyRewritePolicy = new DependencyRewritePolicy(rules.dependencyRewriteRules());
        this.filePolicy = new FilePolicy(rules.fileRules());
        this.taskPolicy = new TaskPolicy(rules.taskRules());
        this.workspacePolicy = new WorkspacePolicy(rules.workspaceRule());
        this.attachmentsPolicy = new AttachmentsPolicy(rules.attachmentsRule());
        this.containerPolicy = new ContainerPolicy(rules.containerRules());
        this.rawPayloadPolicy = new RawPayloadPolicy(rules.rawPayloadRule());

        QueueRule qr = getQueueRule(rules);
        this.concurrentProcessPolicy = new ConcurrentProcessPolicy(qr.concurrentRule());
        this.forkDepthPolicy = new ForkDepthPolicy(qr.forkDepthRule());
        this.processTimeoutPolicy = new ProcessTimeoutPolicy(qr.processTimeoutRule());

        this.protectedTasksPolicy = new ProtectedTasksPolicy(rules.protectedTasksRules());
        this.entityPolicy = new EntityPolicy(rules.entityRules());
        this.processCfgPolicy = new ProcessCfgPolicy(rules.processCfg());
        this.jsonStoragePolicy = new JsonStorePolicy(rules.jsonStoreRule());
        this.defaultProcessCfgPolicy = new ProcessCfgPolicy(rules.defaultProcessCfg());
        this.defaultDependencyVersionsPolicy = new DependencyVersionsPolicy(rules.dependencyVersions());
        this.statePolicy = new StatePolicy(rules.stateRules());
        this.runtimePolicy = new RuntimePolicy(rules.runtimeRule());
        this.cronTriggerPolicy = new CronTriggerPolicy(rules.cronTriggerRule());
        this.kvPolicy = new KvPolicy(rules.kvRule());
    }

    public List<String> policyNames() {
        return ruleNames;
    }

    public PolicyEngineRules getRules() {
        return rules;
    }

    public DependencyPolicy getDependencyPolicy() {
        return dependencyPolicy;
    }

    public DependencyRewritePolicy getDependencyRewritePolicy() {
        return dependencyRewritePolicy;
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

    public AttachmentsPolicy getAttachmentsPolicy() {
        return attachmentsPolicy;
    }

    public ContainerPolicy getContainerPolicy() {
        return containerPolicy;
    }

    public ConcurrentProcessPolicy getConcurrentProcessPolicy() {
        return concurrentProcessPolicy;
    }

    public ForkDepthPolicy getForkDepthPolicy() {
        return forkDepthPolicy;
    }

    public ProcessTimeoutPolicy getProcessTimeoutPolicy() {
        return processTimeoutPolicy;
    }

    public ProtectedTasksPolicy getProtectedTasksPolicy() {
        return protectedTasksPolicy;
    }

    public EntityPolicy getEntityPolicy() {
        return entityPolicy;
    }

    public ProcessCfgPolicy getProcessCfgPolicy() {
        return processCfgPolicy;
    }

    public JsonStorePolicy getJsonStoragePolicy() {
        return jsonStoragePolicy;
    }

    public ProcessCfgPolicy getDefaultProcessCfgPolicy() {
        return defaultProcessCfgPolicy;
    }

    public DependencyVersionsPolicy getDefaultDependencyVersionsPolicy() {
        return defaultDependencyVersionsPolicy;
    }

    public StatePolicy getStatePolicy() {
        return statePolicy;
    }

    public RawPayloadPolicy getRawPayloadPolicy() {
        return rawPayloadPolicy;
    }
    
    public RuntimePolicy getRuntimePolicy() {
        return runtimePolicy;
    }

    public CronTriggerPolicy getCronTriggerPolicy() {
        return cronTriggerPolicy;
    }

    public KvPolicy getKvPolicy() {
        return kvPolicy;
    }

    @Override
    public String toString() {
        if (ruleNames == null) {
            return "no rules defined";
        }

        return String.join(", ", ruleNames);
    }

    private static QueueRule getQueueRule(PolicyEngineRules rules) {
        if (rules.queueRules() == null) {
            return QueueRule.empty();
        }

        return rules.queueRules();
    }
}
