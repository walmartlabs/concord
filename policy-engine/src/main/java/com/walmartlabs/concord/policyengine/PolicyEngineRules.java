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

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Value.Immutable
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonSerialize(as = ImmutablePolicyEngineRules.class)
@JsonDeserialize(as = ImmutablePolicyEngineRules.class)
public interface PolicyEngineRules extends Serializable {

    @JsonProperty("dependency")
    @Nullable
    PolicyRules<DependencyRule> dependencyRules();

    @JsonProperty("dependencyRewrite")
    @Nullable
    List<DependencyRewriteRule> dependencyRewriteRules();

    @JsonProperty("file")
    @Nullable
    PolicyRules<FileRule> fileRules();

    @JsonProperty("task")
    @Nullable
    PolicyRules<TaskRule> taskRules();

    @JsonProperty("workspace")
    @Nullable
    WorkspaceRule workspaceRule();

    @JsonProperty("attachments")
    @Nullable
    AttachmentsRule attachmentsRule();

    @JsonProperty("container")
    @Nullable
    ContainerRule containerRules();

    @JsonProperty("queue")
    @Nullable
    QueueRule queueRules();

    @JsonProperty("protectedTask")
    @Nullable
    ProtectedTasksRule protectedTasksRules();

    @JsonProperty("entity")
    @Nullable
    PolicyRules<EntityRule> entityRules();

    @JsonProperty("processCfg")
    @Nullable
    Map<String, Object> processCfg();

    @JsonAnySetter
    @JsonAnyGetter
    @Nullable
    Map<String, Object> customRule();

    @JsonProperty("jsonStore")
    @Nullable
    JsonStoreRule jsonStoreRule();

    @JsonProperty("defaultProcessCfg")
    @Nullable
    Map<String, Object> defaultProcessCfg();

    @JsonProperty("dependencyVersions")
    @Nullable
    List<DependencyVersionsPolicy.Dependency> dependencyVersions();

    @JsonProperty("state")
    @Nullable
    PolicyRules<StateRule> stateRules();

    @JsonProperty("rawPayload")
    @Nullable
    RawPayloadRule rawPayloadRule();

    @JsonProperty("runtime")
    @Nullable
    RuntimeRule runtimeRule();

    @JsonProperty("cronTrigger")
    @Nullable
    CronTriggerRule cronTriggerRule();

    @JsonProperty("kv")
    @Nullable
    KvRule kvRule();

    static ImmutablePolicyEngineRules.Builder builder() {
        return ImmutablePolicyEngineRules.builder();
    }
}
