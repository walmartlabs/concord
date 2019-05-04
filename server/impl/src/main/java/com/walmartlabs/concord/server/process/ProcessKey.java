package com.walmartlabs.concord.server.process;

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

import java.sql.Timestamp;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite key of a process. Identifies process resources in partitioned tables.
 */
public class ProcessKey extends PartialProcessKey implements com.walmartlabs.concord.server.sdk.ProcessKey {

    private static final long serialVersionUID = 1L;

    public static ProcessKey from(ProcessEntry e) {
        return new ProcessKey(e.instanceId(), new Timestamp(e.createdAt().getTime()));
    }

    private final Timestamp createdAt;

    public ProcessKey(PartialProcessKey part, Timestamp createdAt) {
        this(part.getInstanceId(), createdAt);
    }

    public ProcessKey(UUID instanceId, Timestamp createdAt) {
        super(instanceId);
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ProcessKey that = (ProcessKey) o;
        return Objects.equals(createdAt, that.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), createdAt);
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public String toString() {
        return getInstanceId().toString();
    }
}
