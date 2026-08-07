package com.walmartlabs.concord.runtime.v25.runner.persistence;

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

import com.walmartlabs.concord.runtime.v25.model.Values;
import com.walmartlabs.concord.runtime.v25.runner.engine.ProcessStatus;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public record State25(int formatVersion, String planId, String entryPoint, ProcessStatus status,
                      String terminalIntent, String checkpointName, Map<String, Object> checkpointMetadata,
                      long createdAtEpochMilli, FiberState root, List<WaitState> waits,
                      List<HistoryState> history,
                      /** Active correlation-route-to-segment mappings preserved while a process is suspended. */
                      Map<String, Long> logSegments) implements Serializable {
    public static final int CURRENT_FORMAT = 4;

    /**
     * Creates state without suspended log-segment routing for source compatibility with older snapshot producers.
     */
    public State25(int formatVersion, String planId, String entryPoint, ProcessStatus status,
                   String terminalIntent, String checkpointName, Map<String, Object> checkpointMetadata,
                   long createdAtEpochMilli, FiberState root, List<WaitState> waits,
                   List<HistoryState> history) {
        this(formatVersion, planId, entryPoint, status, terminalIntent, checkpointName, checkpointMetadata,
                createdAtEpochMilli, root, waits, history, Map.of());
    }

    public State25 {
        if (formatVersion != CURRENT_FORMAT) {
            throw new IllegalArgumentException("State format must be " + CURRENT_FORMAT + ", got " + formatVersion);
        }
        if (planId == null || planId.isBlank()) {
            throw new IllegalArgumentException("State planId must not be empty");
        }
        if (entryPoint == null || entryPoint.isBlank()) {
            throw new IllegalArgumentException("State entryPoint must not be empty");
        }
        checkpointMetadata = Values.map(checkpointMetadata);
        waits = List.copyOf(waits);
        history = List.copyOf(history);
        logSegments = Map.copyOf(logSegments);
    }

    public record FiberState(long id, Long parentId, FiberStatus status, int rootScopeId,
                             List<ScopeState> scopes, List<FrameState> continuation,
                             List<FiberState> children) implements Serializable {

        public FiberState {
            scopes = List.copyOf(scopes);
            continuation = List.copyOf(continuation);
            children = List.copyOf(children);
        }
    }

    public enum FiberStatus {
        RUNNABLE,
        WAITING,
        BARRIER,
        COMPLETED
    }

    public record ScopeState(int id, Integer parentId, String flowName, boolean dryRun, boolean debug,
                             Map<String, Object> overlay) implements Serializable {

        public ScopeState {
            overlay = Values.map(overlay);
        }
    }

    public sealed interface FrameState extends Serializable permits SequenceState, StepState {
    }
    public record SequenceState(List<Integer> instructionIds, int programCounter, int scopeId,
                                Integer outputTargetId, Object outputDescriptor, boolean flow,
                                Integer outputInstructionId)
            implements FrameState {

        public SequenceState {
            instructionIds = List.copyOf(instructionIds);
            outputDescriptor = Values.freeze(outputDescriptor);
        }
    }

    public record StepState(int instructionId, int parentScopeId, String phase, boolean configurationResolved,
                            LoopState loop, RetryState retry, int itemIndex, int attempt, Integer workScopeId,
                            Integer handlerScopeId, FailureState originalFailure,
                            Map<String, Object> accumulated, WaitState waitState,
                            ParallelState parallel) implements FrameState {

        public StepState {
            accumulated = Values.map(accumulated);
        }
    }

    public record LoopState(List<Object> items, boolean parallel, int parallelism) implements Serializable {

        public LoopState {
            items = Values.list(items);
        }
    }

    public record RetryState(int times, long delayMillis, Object input, Long deadlineEpochMilli)
            implements Serializable {

        public RetryState {
            input = Values.freeze(input);
        }
    }
    public record ParallelState(int count, int limit, int nextIndex, List<ChildState> children,
                                List<HistoryState> historySnapshot) implements Serializable {

        public ParallelState {
            children = List.copyOf(children);
            historySnapshot = List.copyOf(historySnapshot);
        }
    }

    public record ChildState(int index, String status, Map<String, Object> values,
                             FailureState failure, FiberState fiber, List<WaitState> waits,
                             List<Integer> instructionIds, Integer branchIndex, Integer loopItemIndex,
                             String capture, List<String> outputNames, List<HistoryState> history)
            implements Serializable {

        public ChildState {
            values = Values.map(values);
            waits = List.copyOf(waits);
            instructionIds = List.copyOf(instructionIds);
            outputNames = List.copyOf(outputNames);
            history = List.copyOf(history);
        }
    }

    public record HistoryState(String taskName, Map<String, Object> result, boolean successful)
            implements Serializable {

        public HistoryState {
            result = Values.map(result);
        }
    }

    public record WaitState(String eventName, boolean reentrant, String taskName,
                            Map<String, Object> payload, int instructionId, String source,
                            int line, int column, String path, int scopeId, long fiberId)
            implements Serializable {

        public WaitState {
            if (eventName == null || eventName.isBlank()) {
                throw new IllegalArgumentException("Wait event name must not be empty");
            }
            payload = Values.map(payload);
        }
    }

    public record FailureState(String type, String message, Map<String, Object> payload)
            implements Serializable {

        public FailureState {
            payload = Values.map(payload);
        }
    }
}
