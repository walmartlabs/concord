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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.UUID;

@Value.Immutable
@JsonSerialize(as = ImmutableProcessCompletionCondition.class)
@JsonDeserialize(as = ImmutableProcessCompletionCondition.class)
public abstract class ProcessCompletionCondition extends AbstractWaitCondition {

    @Nullable
    public abstract String resumeEvent();

    public abstract Set<UUID> processes();

    // TODO: remove @Nullable when all conditions migrated to the new format
    @Nullable
    public abstract Set<ProcessStatus> finalStatuses();

    @Value.Default
    public CompleteCondition completeCondition() {
        return CompleteCondition.ALL;
    }

    @Override
    public WaitType type() {
        return WaitType.PROCESS_COMPLETION;
    }

    public static ImmutableProcessCompletionCondition.Builder builder() {
        return ImmutableProcessCompletionCondition.builder();
    }

    public enum CompleteCondition {
        // all processes are finished
        ALL,
        // one of the processes is finished
        ONE_OF
    }
}
