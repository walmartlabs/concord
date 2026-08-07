package com.walmartlabs.concord.runtime.v25.runner;

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

import com.walmartlabs.concord.runtime.common.FormService;
import com.walmartlabs.concord.runtime.v25.runner.engine.Engine;
import com.walmartlabs.concord.runtime.v25.runner.engine.RetryScheduler;
import com.walmartlabs.concord.runtime.v25.runner.expression.ExpressionService;
import com.walmartlabs.concord.runtime.v25.runner.persistence.CheckpointStore;
import com.walmartlabs.concord.runtime.v25.runner.persistence.State25;
import com.walmartlabs.concord.runtime.v25.runner.task.TaskEnvironment;
import com.walmartlabs.concord.runtime.v25.runner.task.TaskRegistry;
import com.walmartlabs.concord.runtime.v25.runner.task.TaskRuntime;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public final class EngineFixture {

    private static final int DEFAULT_MAX_CALL_DEPTH = 256;
    private static final int DEFAULT_WORKER_PARALLELISM = 64;
    private static final Duration DEFAULT_CANCELLATION_GRACE = Duration.ofSeconds(5);
    private static final Path WORKING_DIRECTORY = Path.of("target", "v25-engine-fixture");
    private static final CheckpointStore NO_CHECKPOINTS = new CheckpointStore() {
        @Override
        public void save(String name, State25 state) {
        }

        @Override
        public State25 load() {
            return null;
        }
    };

    private EngineFixture() {
    }

    public static Engine engine(ExpressionService expressions) {
        return engine(expressions, DEFAULT_MAX_CALL_DEPTH, emptyTaskRuntime(), DEFAULT_WORKER_PARALLELISM,
                RetryScheduler.SYSTEM, DEFAULT_CANCELLATION_GRACE, NO_CHECKPOINTS, forms());
    }

    public static Engine engine(ExpressionService expressions, int maxCallDepth) {
        return engine(expressions, maxCallDepth, emptyTaskRuntime(), DEFAULT_WORKER_PARALLELISM,
                RetryScheduler.SYSTEM, DEFAULT_CANCELLATION_GRACE, NO_CHECKPOINTS, forms());
    }

    public static Engine engine(ExpressionService expressions, TaskRuntime taskRuntime) {
        return engine(expressions, DEFAULT_MAX_CALL_DEPTH, taskRuntime, DEFAULT_WORKER_PARALLELISM,
                RetryScheduler.SYSTEM, DEFAULT_CANCELLATION_GRACE, NO_CHECKPOINTS, forms());
    }

    public static Engine engine(ExpressionService expressions, int maxCallDepth, TaskRuntime taskRuntime,
                                int workerParallelism, RetryScheduler retryScheduler) {
        return engine(expressions, maxCallDepth, taskRuntime, workerParallelism, retryScheduler,
                DEFAULT_CANCELLATION_GRACE, NO_CHECKPOINTS, forms());
    }

    public static Engine engine(ExpressionService expressions, int maxCallDepth, TaskRuntime taskRuntime,
                                int workerParallelism, RetryScheduler retryScheduler, Duration cancellationGrace) {
        return engine(expressions, maxCallDepth, taskRuntime, workerParallelism, retryScheduler,
                cancellationGrace, NO_CHECKPOINTS, forms());
    }

    public static Engine engine(ExpressionService expressions, int maxCallDepth, TaskRuntime taskRuntime,
                                int workerParallelism, RetryScheduler retryScheduler, Duration cancellationGrace,
                                CheckpointStore checkpointStore) {
        return engine(expressions, maxCallDepth, taskRuntime, workerParallelism, retryScheduler,
                cancellationGrace, checkpointStore, forms());
    }

    public static Engine engine(ExpressionService expressions, int maxCallDepth, TaskRuntime taskRuntime,
                                int workerParallelism, RetryScheduler retryScheduler, Duration cancellationGrace,
                                CheckpointStore checkpointStore, FormService formService) {
        return new Engine(expressions, maxCallDepth, taskRuntime, workerParallelism, retryScheduler,
                cancellationGrace, checkpointStore, formService);
    }

    private static TaskRuntime emptyTaskRuntime() {
        return new TaskRuntime(new TaskRegistry(List.of()), TaskEnvironment.local(WORKING_DIRECTORY));
    }

    private static FormService forms() {
        return new FormService(WORKING_DIRECTORY.resolve("forms"));
    }
}
