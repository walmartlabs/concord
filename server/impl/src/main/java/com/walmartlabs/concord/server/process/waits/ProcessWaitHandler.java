package com.walmartlabs.concord.server.process.waits;

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

import com.walmartlabs.concord.server.sdk.ProcessKey;
import org.immutables.value.Value;
import org.jooq.DSLContext;

import javax.annotation.Nullable;

public interface ProcessWaitHandler<T extends AbstractWaitCondition> {

    WaitType getType();

    Result<T> process(ProcessKey processKey, T waits);

    @Value.Immutable
    interface Result<T extends AbstractWaitCondition> {

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

        static <T extends AbstractWaitCondition> Result<T> of(T waitCondition) {
            return ImmutableResult.of(waitCondition, null, null);
        }

        static <T extends AbstractWaitCondition> Result<T> resume(String resumeEvent) {
            return ImmutableResult.of(null, resumeEvent, null);
        }

        static <T extends AbstractWaitCondition> Result<T> action(Action action) {
            return ImmutableResult.of(null, null, action);
        }
    }

    interface Action {

        void execute(DSLContext tx);
    }
}
