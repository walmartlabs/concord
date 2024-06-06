package com.walmartlabs.concord.runtime.v2.runner.el;

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

import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v2.model.Step;
import com.walmartlabs.concord.runtime.v2.runner.context.ContextVariables;
import com.walmartlabs.concord.runtime.v2.sdk.Execution;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SingleFrameContext extends DummyContext {

    private final Map<String, Object> variables;

    public SingleFrameContext(Map<String, Object> variables) {
        this.variables = variables;
    }

    @Override
    public Execution execution() {
        return new Execution() {
            @Override
            public ThreadId currentThreadId() {
                return null;
            }

            @Override
            public Runtime runtime() {
                throw new IllegalStateException("Not implemented");
            }

            @Override
            public State state() {
                return new State() {

                    private static final long serialVersionUID = 1L;

                    private final List<Frame> frames = Collections.singletonList(Frame.builder()
                            .root()
                            .locals(variables)
                            .build());

                    @Override
                    public void pushFrame(ThreadId threadId, Frame frame) {
                        throw new IllegalStateException("Not implemented");
                    }

                    @Override
                    public Frame peekFrame(ThreadId threadId) {
                        throw new IllegalStateException("Not implemented");
                    }

                    @Override
                    public void popFrame(ThreadId threadId, UnstoppableCommandHandler handler) {
                        throw new IllegalStateException("Not implemented");
                    }

                    @Override
                    public List<Frame> getFrames(ThreadId threadId) {
                        return frames;
                    }

                    @Override
                    public void dropAllFrames() {
                        throw new IllegalStateException("Not implemented");
                    }

                    @Override
                    public void setStatus(ThreadId threadId, ThreadStatus status) {
                        throw new IllegalStateException("Not implemented");
                    }

                    @Override
                    public ThreadStatus getStatus(ThreadId threadId) {
                        throw new IllegalStateException("Not implemented");
                    }

                    @Override
                    public ThreadId getRootThreadId() {
                        throw new IllegalStateException("Not implemented");
                    }

                    @Override
                    public void fork(ThreadId parentThreadId, ThreadId threadId, Command... cmds) {
                        throw new IllegalStateException("Not implemented");
                    }

                    @Override
                    public Map<ThreadId, ThreadStatus> threadStatus() {
                        throw new IllegalStateException("Not implemented");
                    }

                    @Override
                    public ThreadId nextThreadId() {
                        throw new IllegalStateException("Not implemented");
                    }

                    @Override
                    public void setEventRef(ThreadId threadId, String eventRef) {
                        throw new IllegalStateException("Not implemented");
                    }

                    @Override
                    public ThreadId removeEventRef(String eventRef) {
                        throw new IllegalStateException("Not implemented");
                    }

                    @Override
                    public Map<ThreadId, String> getEventRefs() {
                        throw new IllegalStateException("Not implemented");
                    }

                    @Override
                    public void setThreadError(ThreadId threadId, Exception error) {
                        throw new IllegalStateException("Not implemented");
                    }

                    @Override
                    public Exception clearThreadError(ThreadId threadId) {
                        throw new IllegalStateException("Not implemented");
                    }

                    @Override
                    public List<StackTraceItem> getStackTrace(ThreadId threadId) {
                        throw new IllegalStateException("Not implemented");
                    }

                    @Override
                    public void pushStackTraceItem(ThreadId threadId, StackTraceItem item) {
                        throw new IllegalStateException("Not implemented");
                    }

                    @Override
                    public void clearStackTrace(ThreadId threadId) {
                        throw new IllegalStateException("Not implemented");
                    }

                    @Override
                    public void gc() {
                        throw new IllegalStateException("Not implemented");
                    }
                };
            }

            @Override
            public ProcessDefinition processDefinition() {
                throw new IllegalStateException("Not implemented");
            }

            @Nullable
            @Override
            public Step currentStep() {
                return null;
            }

            @Override
            public String currentFlowName() {
                return null;
            }

            @Override
            public UUID correlationId() {
                throw new IllegalStateException("Not implemented");
            }
        };
    }

    @Override
    public Variables variables() {
        return new ContextVariables(this);
    }

    @Override
    public Variables defaultVariables() {
        throw new IllegalStateException("Not implemented");
    }
}
