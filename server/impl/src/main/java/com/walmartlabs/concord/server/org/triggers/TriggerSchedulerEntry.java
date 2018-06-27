package com.walmartlabs.concord.server.org.triggers;

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


import java.io.Serializable;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

public class TriggerSchedulerEntry implements Serializable {

    private final Date fireAt;

    private final UUID triggerId;

    private final UUID orgId;

    private final UUID projectId;

    private final UUID repoId;

    private final String entryPoint;

    private final String cronSpec;

    private final Map<String, Object> arguments;

    public TriggerSchedulerEntry(Date fireAt, UUID triggerId, UUID orgId, UUID projectId, UUID repoId, String entryPoint,
                                 String cronSpec, Map<String, Object> arguments) {
        this.fireAt = fireAt;
        this.triggerId = triggerId;
        this.orgId = orgId;
        this.projectId = projectId;
        this.repoId = repoId;
        this.entryPoint = entryPoint;
        this.cronSpec = cronSpec;
        this.arguments = arguments;
    }

    public Date getFireAt() {
        return fireAt;
    }

    public UUID getTriggerId() {
        return triggerId;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getRepoId() {
        return repoId;
    }

    public String getEntryPoint() {
        return entryPoint;
    }

    public String getCronSpec() {
        return cronSpec;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    @Override
    public String toString() {
        return "TriggerSchedulerEntry{" +
                "fireAt=" + fireAt +
                ", triggerId=" + triggerId +
                ", orgId=" + orgId +
                ", projectId=" + projectId +
                ", repoId=" + repoId +
                ", entryPoint='" + entryPoint + '\'' +
                ", cronSpec='" + cronSpec + '\'' +
                ", arguments=" + arguments +
                '}';
    }
}
