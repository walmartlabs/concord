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

import com.walmartlabs.concord.server.sdk.ProcessKey;
import org.immutables.value.Value;
import org.jooq.DSLContext;

import javax.annotation.Nullable;
import java.util.List;

public interface ProcessWaitHandler<T extends AbstractWaitCondition> {

    WaitType getType();

    List<Result<T>> processBatch(List<WaitConditionItem<T>> waits);

    @Value.Immutable
    interface WaitConditionItem<T extends AbstractWaitCondition> {

        @Value.Parameter
        ProcessKey processKey();

        @Value.Parameter
        int waitConditionId();

        @Value.Parameter
        T waitCondition();

        static <T extends AbstractWaitCondition> WaitConditionItem<T> of(ProcessKey processKey, int waitConditionId, T waitCondition) {
            return ImmutableWaitConditionItem.of(processKey, waitConditionId, waitCondition);
        }
    }

    @Value.Immutable
    interface Result<T extends AbstractWaitCondition> {

        @Value.Parameter
        ProcessKey processKey();

        @Value.Parameter
        int waitConditionId();

        /**
         * return null if the process doesn't have any wait conditions.
         */
        @Value.Parameter
        @Nullable
        T waitCondition();

        /**
         * resume event for resume process.
         */
        @Value.Parameter
        @Nullable
        String resumeEvent();

        @Value.Parameter
        @Nullable
        Action action();

        static <T extends AbstractWaitCondition> Result<T> of(ProcessKey processKey, int waitConditionId, T waitCondition) {
            return ImmutableResult.of(processKey, waitConditionId, waitCondition, null, null);
        }

        static <T extends AbstractWaitCondition> Result<T> resume(WaitConditionItem<?> wait, String resumeEvent) {
            return ImmutableResult.of(wait.processKey(), wait.waitConditionId(), null, resumeEvent, null);
        }

        static <T extends AbstractWaitCondition> Result<T> resume(ProcessKey processKey, int waitConditionId, String resumeEvent) {
            return ImmutableResult.of(processKey, waitConditionId, null, resumeEvent, null);
        }

        static <T extends AbstractWaitCondition> Result<T> action(WaitConditionItem<?> wait, Action action) {
            return ImmutableResult.of(wait.processKey(), wait.waitConditionId(), null, null, action);
        }
    }

    interface Action {

        void execute(DSLContext tx);
    }
}
