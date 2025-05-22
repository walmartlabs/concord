package com.walmartlabs.concord.process.loader.v2;

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

import com.walmartlabs.concord.runtime.model.*;
import com.walmartlabs.concord.runtime.v2.model.Expression;
import com.walmartlabs.concord.runtime.v2.model.TaskCall;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FlowDefinitionV2 implements FlowDefinition {

    private static final long serialVersionUID = 1L;

    private final String name;
    private final List<Step> steps;

    public FlowDefinitionV2(String name, List<com.walmartlabs.concord.runtime.v2.model.Step> steps) {
        this.name = name;
        this.steps = new ArrayList<>();
        steps.stream()
                .map(this::toStep)
                .filter(Objects::nonNull)
                .forEach(this.steps::add);
    }

    private Step toStep(com.walmartlabs.concord.runtime.v2.model.Step s) {
        if (s instanceof TaskCall) {
            TaskCall task = (TaskCall)s;
            return TaskCallStep.builder()
                    .name(task.getName())
                    .input(task.getOptions().input())
                    .location(SourceMap.from(task.getLocation()))
                    .build();
        } else if (s instanceof Expression) {
            Expression expression = (Expression)s;
            return ExpressionStep.builder()
                    .expression(expression.getExpr())
                    .location(SourceMap.from(expression.getLocation()))
                    .build();
        }
        return null;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public List<Step> steps() {
        return steps;
    }
}
