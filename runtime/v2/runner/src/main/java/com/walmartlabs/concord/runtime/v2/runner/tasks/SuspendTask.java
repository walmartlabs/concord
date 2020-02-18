package com.walmartlabs.concord.runtime.v2.runner.tasks;

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

import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskContext;
import com.walmartlabs.concord.svm.State;
import com.walmartlabs.concord.svm.ThreadId;
import com.walmartlabs.concord.svm.commands.Suspend;

import javax.inject.Named;
import java.io.Serializable;
import java.util.Map;

@Named("suspend")
public class SuspendTask implements Task {

    @Override
    public Serializable execute(TaskContext ctx) {
        Map<String, Object> input = ctx.input();
        String eventRef = (String) input.get("0");

        State s = ctx.execution().state();

        ThreadId eid = ctx.execution().currentThreadId();
        s.peekFrame(eid).push(new Suspend(eventRef));
        return null;
    }
}
