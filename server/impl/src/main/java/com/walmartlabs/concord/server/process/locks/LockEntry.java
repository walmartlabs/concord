package com.walmartlabs.concord.server.process.locks;

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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.walmartlabs.concord.server.jooq.enums.ProcessLockScope;
import org.immutables.value.Value;

import java.util.UUID;

@Value.Immutable
@JsonSerialize(as = ImmutableLockEntry.class)
@JsonDeserialize(as = ImmutableLockEntry.class)
public interface LockEntry {

    UUID instanceId();

    UUID orgId();

    UUID projectId();

    String name();

    ProcessLockScope scope();

    static ImmutableLockEntry.Builder builder() {
        return ImmutableLockEntry.builder();
    }
}
