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

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Partial key of a process. Contains only the ID and can be used to
 * start a new process or to retrieve the complete process key {@link ProcessKey}.
 */
public class PartialProcessKey implements Serializable {

    /**
     * Creates a partial process key from a known instance ID.
     */
    public static PartialProcessKey from(UUID instanceId) {
        return new PartialProcessKey(instanceId);
    }

    /**
     * Creates a new unique partial process key.
     */
    public static PartialProcessKey create() {
        return PartialProcessKey.from(UUID.randomUUID());
    }

    private final UUID instanceId;

    protected PartialProcessKey(UUID instanceId) {
        this.instanceId = instanceId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PartialProcessKey that = (PartialProcessKey) o;
        return Objects.equals(instanceId, that.instanceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instanceId);
    }

    public boolean partOf(PartialProcessKey p) {
        return instanceId.equals(p.getInstanceId());
    }

    public UUID getInstanceId() {
        return instanceId;
    }

    @Override
    public String toString() {
        return instanceId.toString();
    }
}
