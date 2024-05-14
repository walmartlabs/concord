package com.walmartlabs.concord.runtime.v2.runner.tasks;

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

import com.walmartlabs.concord.common.AllowNulls;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v2.model.Step;
import com.walmartlabs.concord.svm.ThreadId;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Value.Immutable
@Value.Style(jdkOnly = true)
public interface TaskCallEvent {

    Phase phase();

    ProcessDefinition processDefinition();

    Step currentStep();

    String taskName();

    String methodName();

    ThreadId threadId();

    @AllowNulls
    @Value.Default
    default List<Object> input() {
        return Collections.emptyList();
    }

    @AllowNulls
    @Value.Default
    default List<List<Annotation>> inputAnnotations() {
        return Collections.emptyList();
    }

    UUID correlationId();

    @Nullable
    Long duration();

    @Nullable
    Serializable result();

    @Nullable
    String error();

    @Value.Default
    @AllowNulls
    default Map<String, Serializable> meta() {
        return Collections.emptyMap();
    }

    static ImmutableTaskCallEvent.Builder builder() {
        return ImmutableTaskCallEvent.builder();
    }

    enum Phase {

        PRE,
        POST
    }
}
