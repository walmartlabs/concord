package com.walmartlabs.concord.server.process.waits;

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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.walmartlabs.concord.server.jooq.enums.ProcessLockScope;
import com.walmartlabs.concord.server.process.locks.LockEntry;
import org.immutables.value.Value;

import java.util.UUID;

@Value.Immutable
@JsonSerialize(as = ImmutableProcessLockCondition.class)
@JsonDeserialize(as = ImmutableProcessLockCondition.class)
public abstract class ProcessLockCondition extends AbstractWaitCondition {

    private static final long serialVersionUID = 1L;

    public abstract UUID instanceId();

    public abstract UUID orgId();

    public abstract UUID projectId();

    public abstract ProcessLockScope scope();

    public abstract String name();

    @Override
    public WaitType type() {
        return WaitType.PROCESS_LOCK;
    }

    @Override
    public boolean exclusive() {
        return false;
    }

    public static ImmutableProcessLockCondition.Builder builder() {
        return ImmutableProcessLockCondition.builder();
    }

    public static ProcessLockCondition from(LockEntry e) {
        return builder()
                .instanceId(e.instanceId())
                .orgId(e.orgId())
                .projectId(e.projectId())
                .scope(e.scope())
                .name(e.name())
                .build();
    }
}
