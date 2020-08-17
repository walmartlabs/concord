package com.walmartlabs.concord.server.audit;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.walmartlabs.concord.server.org.EntityOwner;
import com.walmartlabs.concord.server.sdk.AllowNulls;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.time.OffsetDateTime;
import java.util.Map;

@Value.Immutable
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonSerialize(as = ImmutableAuditLogEntry.class)
@JsonDeserialize(as = ImmutableAuditLogEntry.class)
public interface AuditLogEntry {

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
    OffsetDateTime entryDate();

    AuditAction action();

    AuditObject object();

    @Nullable
    @AllowNulls
    Map<String, Object> details();

    @Nullable
    EntityOwner user();

    static ImmutableAuditLogEntry.Builder builder() {
        return ImmutableAuditLogEntry.builder();
    }
}
