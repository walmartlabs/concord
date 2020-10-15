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

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PolicyEngineRules {

    private final PolicyRules<DependencyRule> dependencyRules;
    private final PolicyRules<FileRule> fileRules;
    private final PolicyRules<TaskRule> taskRules;
    private final WorkspaceRule workspaceRule;
    private final AttachmentsRule attachmentsRule;
    private final ContainerRule containerRules;
    private final QueueRule queueRules;
    private final ProtectedTasksRule protectedTasksRules;
    private final PolicyRules<EntityRule> entityRules;
    private final Map<String, Object> processCfg;
    private final Map<String, Object> customRule;
    private final JsonStoreRule jsonStoreRule;
    private final Map<String, Object> defaultProcessCfg;
    private final List<DependencyVersionsPolicy.Dependency> dependencyVersions;
    private final PolicyRules<StateRule> stateRules;

    public PolicyEngineRules(@JsonProperty("dependency") PolicyRules<DependencyRule> dependencyRules,
                             @JsonProperty("file") PolicyRules<FileRule> fileRules,
                             @JsonProperty("task") PolicyRules<TaskRule> taskRules,
                             @JsonProperty("workspace") WorkspaceRule workspaceRule,
                             @JsonProperty("container") ContainerRule containerRules,
                             @JsonProperty("queue") QueueRule queueRules,
                             @JsonProperty("protectedTask") ProtectedTasksRule protectedTasksRules,
                             @JsonProperty("entity") PolicyRules<EntityRule> entityRules,
                             @JsonProperty("processCfg") Map<String, Object> processCfg,
                             @JsonProperty("jsonStore") JsonStoreRule jsonStoreRule,
                             @JsonProperty("defaultProcessCfg") Map<String, Object> defaultProcessCfg,
                             @JsonProperty("dependencyVersions")List<DependencyVersionsPolicy.Dependency> dependencyVersions,
                             @JsonProperty("attachments") AttachmentsRule attachmentsRule,
                             @JsonProperty("state") PolicyRules<StateRule> stateRules) {

        this.dependencyRules = dependencyRules;
        this.fileRules = fileRules;
        this.taskRules = taskRules;
        this.workspaceRule = workspaceRule;
        this.attachmentsRule = attachmentsRule;
        this.containerRules = containerRules;
        this.queueRules = queueRules;
        this.protectedTasksRules = protectedTasksRules;
        this.entityRules = entityRules;
        this.processCfg = processCfg;
        this.customRule = new HashMap<>();
        this.jsonStoreRule = jsonStoreRule;
        this.defaultProcessCfg = defaultProcessCfg;
        this.dependencyVersions = dependencyVersions;
        this.stateRules = stateRules;
    }

    public PolicyRules<DependencyRule> getDependencyRules() {
        return dependencyRules;
    }

    public PolicyRules<FileRule> getFileRules() {
        return fileRules;
    }

    public PolicyRules<TaskRule> getTaskRules() {
        return taskRules;
    }

    public WorkspaceRule getWorkspaceRule() {
        return workspaceRule;
    }

    public ContainerRule getContainerRules() {
        return containerRules;
    }

    public QueueRule getQueueRules() {
        return queueRules;
    }

    public ProtectedTasksRule getProtectedTasksRules() {
        return protectedTasksRules;
    }

    public PolicyRules<EntityRule> getEntityRules() {
        return entityRules;
    }

    @JsonProperty("processCfg")
    public Map<String, Object> getProcessCfgRules() {
        return processCfg;
    }

    @JsonProperty("defaultProcessCfg")
    public Map<String, Object> getDefaultProcessCfg() {
        return defaultProcessCfg;
    }

    @JsonProperty("dependencyVersions")
    public List<DependencyVersionsPolicy.Dependency> getDependencyVersions() {
        return dependencyVersions;
    }

    @JsonAnySetter
    public void addCustomRule(String name, Object value) {
        customRule.put(name, value);
    }

    @JsonAnyGetter
    public Map<String, Object> getCustomRule() {
        return customRule;
    }

    public JsonStoreRule getJsonStoreRule() {
        return jsonStoreRule;
    }

    public AttachmentsRule getAttachmentsRule() {
        return attachmentsRule;
    }

    public PolicyRules<StateRule> getStateRules() {
        return stateRules;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PolicyEngineRules that = (PolicyEngineRules) o;
        return Objects.equals(dependencyRules, that.dependencyRules) &&
                Objects.equals(fileRules, that.fileRules) &&
                Objects.equals(taskRules, that.taskRules) &&
                Objects.equals(workspaceRule, that.workspaceRule) &&
                Objects.equals(attachmentsRule, that.attachmentsRule) &&
                Objects.equals(containerRules, that.containerRules) &&
                Objects.equals(queueRules, that.queueRules) &&
                Objects.equals(protectedTasksRules, that.protectedTasksRules) &&
                Objects.equals(entityRules, that.entityRules) &&
                Objects.equals(processCfg, that.processCfg) &&
                Objects.equals(customRule, that.customRule) &&
                Objects.equals(jsonStoreRule, that.jsonStoreRule) &&
                Objects.equals(defaultProcessCfg, that.defaultProcessCfg) &&
                Objects.equals(dependencyVersions, that.dependencyVersions) &&
                Objects.equals(stateRules, that.stateRules);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dependencyRules, fileRules, taskRules, workspaceRule, attachmentsRule, containerRules, queueRules, protectedTasksRules, entityRules, processCfg, customRule, jsonStoreRule, defaultProcessCfg, dependencyVersions, stateRules);
    }

    @Override
    public final String toString() {
        return "PolicyEngineRules{" +
                "dependencyRules=" + dependencyRules +
                ", fileRules=" + fileRules +
                ", taskRules=" + taskRules +
                ", workspaceRule=" + workspaceRule +
                ", attachmentsRule=" + attachmentsRule +
                ", containerRules=" + containerRules +
                ", queueRules=" + queueRules +
                ", protectedTasksRules=" + protectedTasksRules +
                ", entityRules=" + entityRules +
                ", processCfg=" + processCfg +
                ", customRule=" + customRule +
                ", jsonStoreRule=" + jsonStoreRule +
                ", defaultProcessCfg=" + defaultProcessCfg +
                ", dependencyVersions=" + dependencyVersions +
                ", stateRules=" + stateRules +
                '}';
    }
}
