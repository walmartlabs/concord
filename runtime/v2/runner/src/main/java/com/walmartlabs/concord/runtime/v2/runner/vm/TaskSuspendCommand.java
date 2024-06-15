package com.walmartlabs.concord.runtime.v2.runner.vm;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.model.TaskCall;
import com.walmartlabs.concord.runtime.v2.runner.context.ResumeEventImpl;
import com.walmartlabs.concord.runtime.v2.runner.logging.LogContext;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;

import java.io.Serializable;
import java.util.Map;
import java.util.UUID;

public class TaskSuspendCommand implements Command {

    private static final long serialVersionUID = 1L;

    private final UUID correlationId;
    private final LogContext logContext;
    private final String eventName;
    private final TaskCall step;
    private final Map<String, Serializable> taskState;

    public TaskSuspendCommand(UUID correlationId, LogContext logContext, String eventName, TaskCall step, Map<String, Serializable> taskState) {
        this.correlationId = correlationId;
        this.logContext = logContext;
        this.eventName = eventName;
        this.step = step;
        this.taskState = taskState;
    }

    @Override
    public Command copy() {
        return new TaskSuspendCommand(correlationId, logContext, eventName, step, taskState);
    }

    @Override
    public void eval(Runtime runtime, State state, ThreadId threadId) {
        Frame frame = state.peekFrame(threadId);
        frame.pop();

        frame.push(new TaskResumeCommand(correlationId, logContext, step, new ResumeEventImpl(eventName, taskState)));
        frame.push(new SuspendCommand(eventName));
    }
}
