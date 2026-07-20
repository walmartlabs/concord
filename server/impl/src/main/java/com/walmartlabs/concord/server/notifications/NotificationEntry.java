package com.walmartlabs.concord.server.notifications;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private final UUID id;

    @Nullable
    private final UUID userId;

    @Nullable
    private final UUID orgId;

    private final UUID projectId;

    @Nullable
    private final UUID repoId;

    private final String summary;
    private final String body;
    private final String actionLink;
    private final boolean triggerEmail;

    @Nullable
    private final OffsetDateTime dismissedTimestamp;

    @Nullable
    private final UUID dismissedBy;

    @JsonCreator
    public NotificationEntry(@JsonProperty("id") UUID id,
                             @JsonProperty("userId") UUID userId,
                             @JsonProperty("orgId") UUID orgId,
                             @JsonProperty("projectId") UUID projectId,
                             @JsonProperty("repoId") UUID repoId,
                             @JsonProperty("summary") String summary,
                             @JsonProperty("body") String body,
                             @JsonProperty("actionLink") String actionLink,
                             @JsonProperty("triggerEmail") boolean triggerEmail,
                             @JsonProperty("dismissedTimestamp") OffsetDateTime dismissedTimestamp,
                             @JsonProperty("dismissedBy") UUID dismissedBy) {
        this.id = id;
        this.userId = userId;
        this.orgId = orgId;
        this.projectId = projectId;
        this.repoId = repoId;
        this.summary = summary;
        this.body = body;
        this.actionLink = actionLink;
        this.triggerEmail = triggerEmail;
        this.dismissedTimestamp = dismissedTimestamp;
        this.dismissedBy = dismissedBy;
    }

    public UUID getId() {
        return id;
    }

    @Nullable
    public UUID getUserId() {
        return userId;
    }

    @Nullable
    public UUID getOrgId() {
        return orgId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    @Nullable
    public UUID getRepoId() {
        return repoId;
    }

    public String getSummary() {
        return summary;
    }

    public String getBody() {
        return body;
    }

    public String getActionLink() {
        return actionLink;
    }

    public boolean isTriggerEmail() {
        return triggerEmail;
    }

    @Nullable
    public OffsetDateTime getDismissedTimestamp() {
        return dismissedTimestamp;
    }

    @Nullable
    public UUID getDismissedBy() {
        return dismissedBy;
    }

    @Override
    public String toString() {
        return "NotificationEntry{" +
                "id=" + id +
                ", userId=" + userId +
                ", orgId=" + orgId +
                ", projectId=" + projectId +
                ", repoId=" + repoId +
                ", summary='" + summary + '\'' +
                ", body='" + body + '\'' +
                ", actionLink='" + actionLink + '\'' +
                ", triggerEmail=" + triggerEmail +
                ", dismissedTimestamp=" + dismissedTimestamp +
                ", dismissedBy=" + dismissedBy +
                '}';
    }
}
