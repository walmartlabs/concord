package com.walmartlabs.concord.server.sdk;

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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite key of a process. Identifies process resources in partitioned tables.
 * @apiNote the "createdAt" portion of the key must be truncated to microseconds.
 */
public class ProcessKey extends PartialProcessKey {

    private static final long serialVersionUID = 1L;

    private final OffsetDateTime createdAt;

    public static ProcessKey random() {
        return new ProcessKey(UUID.randomUUID(), OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS));
    }

    public ProcessKey(PartialProcessKey part, OffsetDateTime createdAt) {
        this(part.getInstanceId(), createdAt);
    }

    @JsonCreator
    public ProcessKey(@JsonProperty("instanceId") UUID instanceId,
                      @JsonProperty("createdAt") OffsetDateTime createdAt) {

        super(instanceId);

        if (createdAt.getNano() != createdAt.truncatedTo(ChronoUnit.MICROS).getNano()) {
            throw new IllegalArgumentException("The process' createdAt must be truncated to microseconds: " + createdAt);
        }

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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public String toString() {
        return getInstanceId().toString();
    }
}
