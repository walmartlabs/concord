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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonTypeName;

public class PolicyEngineRules {

    private final PolicyRules<DependencyRule> dependencyRules;

    private final PolicyRules<FileRule> fileRules;

    private final PolicyRules<TaskRule> taskRules;

    private final WorkspaceRule workspaceRule;

    private final ContainerRule containerRules;

    private final QueueRule queueRules;

    public PolicyEngineRules(@JsonProperty("dependency") PolicyRules<DependencyRule> dependencyRules,
                             @JsonProperty("file") PolicyRules<FileRule> fileRules,
                             @JsonProperty("task") PolicyRules<TaskRule> taskRules,
                             @JsonProperty("workspace") WorkspaceRule workspaceRule,
                             @JsonProperty("container") ContainerRule containerRules,
                             @JsonProperty("queue") QueueRule queueRules) {

        this.dependencyRules = dependencyRules;
        this.fileRules = fileRules;
        this.taskRules = taskRules;
        this.workspaceRule = workspaceRule;
        this.containerRules = containerRules;
        this.queueRules = queueRules;
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

    @Override
    public String toString() {
        return "PolicyEngineRules{" +
                "dependencyRules=" + dependencyRules +
                ", fileRules=" + fileRules +
                ", taskRules=" + taskRules +
                ", workspaceRule=" + workspaceRule +
                ", containerRules=" + containerRules +
                ", queueRules=" + queueRules +
                '}';
    }
}
