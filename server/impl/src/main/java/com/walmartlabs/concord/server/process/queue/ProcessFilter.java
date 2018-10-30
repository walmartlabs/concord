package com.walmartlabs.concord.server.process.queue;

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

import com.walmartlabs.concord.server.process.ProcessStatus;

import java.sql.Timestamp;
import java.util.Set;
import java.util.UUID;

public class ProcessFilter {

    private final Timestamp afterCreatedAt;

    private final Timestamp beforeCreatedAt;

    private final boolean includeWoProjects;

    private final String initiator;

    private final Set<UUID> orgIds;

    private final UUID projectId;

    private final ProcessStatus processStatus;

    private final Set<String> tags;

    protected ProcessFilter(Timestamp afterCreatedAt, Timestamp beforeCreatedAt, boolean includeWoProjects, String initiator, Set<UUID> orgIds, UUID projectId, ProcessStatus processStatus, Set<String> tags) {
        this.afterCreatedAt = afterCreatedAt;
        this.beforeCreatedAt = beforeCreatedAt;
        this.includeWoProjects = includeWoProjects;
        this.initiator = initiator;
        this.orgIds = orgIds;
        this.projectId = projectId;
        this.processStatus = processStatus;
        this.tags = tags;
    }

    public Timestamp getAfterCreatedAt() {
        return afterCreatedAt;
    }

    public Timestamp getBeforeCreatedAt() {
        return beforeCreatedAt;
    }

    public boolean isIncludeWoProjects() {
        return includeWoProjects;
    }

    public String getInitiator() {
        return initiator;
    }

    public Set<UUID> getOrgIds() {
        return orgIds;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public ProcessStatus getProcessStatus() {
        return processStatus;
    }

    public Set<String> getTags() {
        return tags;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Timestamp afterCreatedAt;
        private Timestamp beforeCreatedAt;
        private boolean includeWoProjects;
        private String initiator;
        private Set<UUID> orgIds;
        private UUID projectId;
        private ProcessStatus processStatus;
        private Set<String> tags;

        public Builder projectId(UUID projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder includeWithoutProjects(boolean includeWoProjects) {
            this.includeWoProjects = includeWoProjects;
            return this;
        }

        public Builder ordIds(Set<UUID> orgIds) {
            this.orgIds = orgIds;
            return this;
        }

        public Builder afterCreatedAt(Timestamp afterCreatedAt) {
            this.afterCreatedAt = afterCreatedAt;
            return this;
        }

        public Builder beforeCreatedAt(Timestamp beforeCreatedAt) {
            this.beforeCreatedAt = beforeCreatedAt;
            return this;
        }

        public Builder tags(Set<String> tags) {
            this.tags = tags;
            return this;
        }

        public Builder status(ProcessStatus processStatus) {
            this.processStatus = processStatus;
            return this;
        }

        public Builder initiator(String initiator) {
            this.initiator = initiator;
            return this;
        }

        public ProcessFilter build() {
            return new ProcessFilter(afterCreatedAt, beforeCreatedAt, includeWoProjects, initiator, orgIds, projectId, processStatus, tags);
        }
    }
}
