package com.walmartlabs.concord.runtime.v2.sdk;

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

import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v2.model.Step;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.State;
import com.walmartlabs.concord.svm.ThreadId;

import javax.annotation.Nullable;
import java.util.UUID;

public interface Execution {

    // TODO add current flow name

    ThreadId currentThreadId();

    Runtime runtime();

    State state();

    ProcessDefinition processDefinition();

    @Nullable
    Step currentStep();

    /**
     * ID of the current task or the expression call. Can be used by plugins to
     * correlate their events with the task event.
     *
     * @return the event correlation ID of the current step.
     */
    UUID correlationId();

    // TODO add suspend()
}
