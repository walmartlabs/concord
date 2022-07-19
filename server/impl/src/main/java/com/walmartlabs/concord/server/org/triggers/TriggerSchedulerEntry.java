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

import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.MapUtils;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TriggerSchedulerEntry extends TriggerEntry {

    private static final long serialVersionUID = 1L;

    private final OffsetDateTime fireAt;
    private final UUID triggerId;

    public TriggerSchedulerEntry(OffsetDateTime fireAt, UUID triggerId, UUID orgId, String orgName, UUID projectId,
                                 String projectName, UUID repositoryId, String repositoryName,
                                 Map<String, Object> conditions, Map<String, Object> cfg, List<String> activeProfiles,
                                 Map<String, Object> arguments, String eventSource) {

        super(null, orgId, orgName, projectId, projectName, repositoryId,
                repositoryName, eventSource, activeProfiles, arguments, conditions, cfg);

        this.fireAt = fireAt;
        this.triggerId = triggerId;
    }

    public OffsetDateTime getFireAt() {
        return fireAt;
    }

    public UUID getTriggerId() {
        return triggerId;
    }

    @Override
    public Map<String, Object> getCfg() {
        Map<String, Object> result = super.getCfg();
        return result == null ? Collections.emptyMap() : result;
    }

    public String getEntryPoint() {
        return (String) getCfg().get(Constants.Request.ENTRY_POINT_KEY);
    }

    public TriggerRunAs runAs() {
        return TriggerRunAs.from(MapUtils.getMap(getCfg(), "runAs", Collections.emptyMap()));
    }

    @Override
    public String toString() {
        return "TriggerSchedulerEntry{" +
                "fireAt=" + fireAt +
                ", triggerId=" + triggerId +
                ", orgId=" + this.getOrgId() +
                ", projectId=" + this.getProjectId() +
                ", repoId=" + this.getRepositoryId() +
                ", eventSource=" + this.getEventSource() +
                ", activeProfiles=" + this.getActiveProfiles() +
                ", arguments=" + this.getArguments() +
                ", conditions=" + this.getConditions() +
                ", cfg=" + this.getCfg() +
                '}';
    }
}
