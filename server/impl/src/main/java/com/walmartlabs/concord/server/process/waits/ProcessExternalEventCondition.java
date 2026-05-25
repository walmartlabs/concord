package com.walmartlabs.concord.server.process.waits;

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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Map;

@Value.Immutable
@JsonSerialize(as = ImmutableProcessExternalEventCondition.class)
@JsonDeserialize(as = ImmutableProcessExternalEventCondition.class)
public abstract class ProcessExternalEventCondition extends AbstractWaitCondition implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Unique key for the external event.
     */
    public abstract String externalEvent();

    /**
     * Event key for resuming the process. Must be unique to the process' but
     * can be repeated across multiple external event waits to force resuming
     * when all external event waits for the same resume event are cleared.
     */
    public abstract String resumeEvent();

    /**
     * Indicates if the condition is still waiting for the event.
     */
    public abstract boolean waiting();

    /**
     * Optional payload of variables to set when the event is cleared.
     */
    @Nullable
    public abstract Map<String, Serializable> variables();

    /**
     * Variable name to save externally-provided resume payload variables. Periods
     * {@code .} are used as a delimiter to save the variables in a nested Map/object.
     */
    @Nullable
    public abstract String saveAs();

    @Nullable
    public abstract OffsetDateTime expiresAt();

    @Override
    public WaitType type() {
        return WaitType.EXTERNAL_EVENT;
    }

    @Override
    public boolean exclusive() {
        return false;
    }

    public static ImmutableProcessExternalEventCondition.Builder builder() {
        return ImmutableProcessExternalEventCondition.builder();
    }
}
