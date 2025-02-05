package com.walmartlabs.concord.server.console;

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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.walmartlabs.concord.common.validation.ConcordKey;
import com.walmartlabs.concord.server.org.ImmutableEntityOwner;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.UUID;

@Value.Immutable
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonSerialize(as = ImmutableProcessCardEntry.class)
@JsonDeserialize(as = ImmutableProcessCardEntry.class)
public interface ProcessCardEntry extends Serializable {

    long serialVersionUID = 1L;

    UUID id();

    @ConcordKey
    @Nullable
    String orgName();

    @ConcordKey
    @Nullable
    String projectName();

    @ConcordKey
    @Nullable
    String repoName();

    @Size(max = 256)
    @Nullable
    String entryPoint();

    @Size(max = 128)
    String name();

    @Size(max = 512)
    @Nullable
    String description();

    @Nullable
    String icon();

    boolean isCustomForm();

    @Nullable
    UUID orderId();

    static ImmutableProcessCardEntry.Builder builder() {
        return ImmutableProcessCardEntry.builder();
    }
}
