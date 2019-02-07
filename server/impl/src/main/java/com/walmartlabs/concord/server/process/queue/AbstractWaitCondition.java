package com.walmartlabs.concord.server.process.queue;

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

import java.io.Serializable;
import java.util.Objects;

/**
 * The inheriting classes MUST override {@link #equals(Object)}) if they
 * provide additional fields.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class AbstractWaitCondition implements Serializable {

    private final WaitType type;
    private final String reason;

    protected AbstractWaitCondition(WaitType type, String reason) {
        this.type = type;
        this.reason = reason;
    }

    public WaitType getType() {
        return type;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AbstractWaitCondition that = (AbstractWaitCondition) o;
        return type == that.type &&
                Objects.equals(reason, that.reason);
    }
}
