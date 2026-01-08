package com.walmartlabs.concord.runtime.v2.runner.compiler;

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

import com.walmartlabs.concord.runtime.v2.model.*;
import com.walmartlabs.concord.runtime.v2.runner.vm.*;
import com.walmartlabs.concord.svm.Command;

import javax.inject.Named;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Named
public final class ScriptCallCompiler implements StepCompiler<ScriptCall> {

    @Override
    public boolean accepts(Step step) {
        return step instanceof ScriptCall;
    }

    @Override
    public Command compile(CompilerContext context, ScriptCall step) {
        UUID correlationId = UUID.randomUUID();
        Command cmd = new LogSegmentScopeCommand<>(correlationId, new ScriptCallCommand(correlationId, step), step);

        ScriptCallOptions options = Objects.requireNonNull(step.getOptions());

        Retry retry = options.retry();
        if (retry != null) {
            cmd = new RetryWrapper(cmd, retry, step);
        }

        WithItems withItems = options.withItems();
        if (withItems != null) {
            cmd = WithItemsWrapper.of(cmd, withItems, Collections.emptyList(), Collections.emptyMap(), step);
        }

        Loop loop = options.loop();
        if (loop != null) {
            cmd = LoopWrapper.of(context, cmd, loop, Collections.emptyList(), Collections.emptyMap(), null, step);
        }

        List<Step> errorSteps = options.errorSteps();
        if (!errorSteps.isEmpty()) {
            cmd = new ErrorWrapper(cmd, CompilerUtils.compile(context, errorSteps));
        }

        return cmd;
    }
}
