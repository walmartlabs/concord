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
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommunicationHistoryEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final UUID notificationId;
    private final String emailTo;

    @Nullable
    private final String emailCc;

    @Nullable
    private final String emailBcc;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
    private final OffsetDateTime emailTimestamp;

    @JsonCreator
    public CommunicationHistoryEntry(@JsonProperty("id") UUID id,
                                     @JsonProperty("notificationId") UUID notificationId,
                                     @JsonProperty("emailTo") String emailTo,
                                     @JsonProperty("emailCc") String emailCc,
                                     @JsonProperty("emailBcc") String emailBcc,
                                     @JsonProperty("emailTimestamp") OffsetDateTime emailTimestamp) {
        this.id = id;
        this.notificationId = notificationId;
        this.emailTo = emailTo;
        this.emailCc = emailCc;
        this.emailBcc = emailBcc;
        this.emailTimestamp = emailTimestamp;
    }

    public UUID getId() {
        return id;
    }

    public UUID getNotificationId() {
        return notificationId;
    }

    public String getEmailTo() {
        return emailTo;
    }

    @Nullable
    public String getEmailCc() {
        return emailCc;
    }

    @Nullable
    public String getEmailBcc() {
        return emailBcc;
    }

    public OffsetDateTime getEmailTimestamp() {
        return emailTimestamp;
    }

    @Override
    public String toString() {
        return "CommunicationHistoryEntry{" +
                "id=" + id +
                ", notificationId=" + notificationId +
                ", emailTo='" + emailTo + '\'' +
                ", emailCc='" + emailCc + '\'' +
                ", emailBcc='" + emailBcc + '\'' +
                ", emailTimestamp=" + emailTimestamp +
                '}';
    }
}
