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

    public PolicyEngine(PolicyEngineRules rules) {
        this(Collections.emptyList(), rules);
    }

    public PolicyEngine(String ruleName, PolicyEngineRules rules) {
        this(Collections.singletonList(ruleName), rules);
    }

    public PolicyEngine(List<String> ruleNames, PolicyEngineRules rules) {
        this.ruleNames = ruleNames;
        this.rules = rules;
        this.dependencyPolicy = new DependencyPolicy(rules.getDependencyRules());
        this.dependencyRewritePolicy = new DependencyRewritePolicy(rules.getDependencyRewriteRules());
        this.filePolicy = new FilePolicy(rules.getFileRules());
        this.taskPolicy = new TaskPolicy(rules.getTaskRules());
        this.workspacePolicy = new WorkspacePolicy(rules.getWorkspaceRule());
        this.attachmentsPolicy = new AttachmentsPolicy(rules.getAttachmentsRule());
        this.containerPolicy = new ContainerPolicy(rules.getContainerRules());

        QueueRule qr = getQueueRule(rules);
        this.concurrentProcessPolicy = new ConcurrentProcessPolicy(qr.getConcurrent());
        this.forkDepthPolicy = new ForkDepthPolicy(qr.getForkDepthRule());
        this.processTimeoutPolicy = new ProcessTimeoutPolicy(qr.getProcessTimeoutRule());

        this.protectedTasksPolicy = new ProtectedTasksPolicy(rules.getProtectedTasksRules());
        this.entityPolicy = new EntityPolicy(rules.getEntityRules());
        this.processCfgPolicy = new ProcessCfgPolicy(rules.getProcessCfgRules());
        this.jsonStoragePolicy = new JsonStorePolicy(rules.getJsonStoreRule());
        this.defaultProcessCfgPolicy = new ProcessCfgPolicy(rules.getDefaultProcessCfg());
        this.defaultDependencyVersionsPolicy = new DependencyVersionsPolicy(rules.getDependencyVersions());
        this.statePolicy = new StatePolicy(rules.getStateRules());
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

    @Override
    public String toString() {
        if (ruleNames == null) {
            return "no rules defined";
        }

        return String.join(", ", ruleNames);
    }

    private static QueueRule getQueueRule(PolicyEngineRules rules) {
        if (rules.getQueueRules() == null) {
            return QueueRule.empty();
        }

        return rules.getQueueRules();
    }
}
