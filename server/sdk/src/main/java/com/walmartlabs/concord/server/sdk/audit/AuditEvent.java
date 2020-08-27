package com.walmartlabs.concord.server.sdk.audit;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.walmartlabs.concord.server.sdk.AllowNulls;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Value.Immutable
@Value.Style(jdkOnly = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonSerialize(as = ImmutableAuditEvent.class)
@JsonDeserialize(as = ImmutableAuditEvent.class)
public interface AuditEvent extends Serializable {

    long serialVersionUID = 1L;

    long entrySeq();

    OffsetDateTime entryDate();

    @Nullable
    UUID userId();

    String object();

    String action();

    @AllowNulls
    Map<String, Object> details();

    static ImmutableAuditEvent.Builder builder() {
        return ImmutableAuditEvent.builder();
    }
}
