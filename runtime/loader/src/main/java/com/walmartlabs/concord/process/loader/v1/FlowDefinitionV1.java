package com.walmartlabs.concord.process.loader.v1;

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

import com.walmartlabs.concord.runtime.model.ExpressionStep;
import com.walmartlabs.concord.runtime.model.FlowDefinition;
import com.walmartlabs.concord.runtime.model.Step;
import com.walmartlabs.concord.runtime.model.TaskCallStep;
import io.takari.bpm.model.*;

import java.io.Serializable;
import java.util.*;

public class FlowDefinitionV1 implements FlowDefinition {

    private static final long serialVersionUID = 1L;

    private final String name;
    private final List<Step> steps;

    public FlowDefinitionV1(ProcessDefinition pd) {
        this.name = pd.getName();

        this.steps = new ArrayList<>();
        if (pd.getChildren() != null) {
            pd.getChildren().forEach(c -> {
                SourceMap sm = null;
                if (pd instanceof SourceAwareProcessDefinition) {
                    SourceAwareProcessDefinition sapd = (SourceAwareProcessDefinition) pd;
                    sm = sapd.getSourceMaps().get(c.getId());
                }

                Step step = toStep(c, sm);
                if (step != null) {
                    this.steps.add(step);
                }
            });
        }
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public List<Step> steps() {
        return steps;
    }

    private static Step toStep(AbstractElement element, SourceMap sm) {
        if (element instanceof ServiceTask) {
            ServiceTask task = (ServiceTask) element;
            if (task.getType() == ExpressionType.DELEGATE) {
                return TaskCallStep.builder()
                        .name(task.getExpression())
                        .input(toInput(task.getIn()))
                        .location(new SourceMapV1(sm))
                        .build();
            } else if (task.getType() == ExpressionType.SIMPLE) {
                return ExpressionStep.builder()
                        .expression(task.getExpression())
                        .input(toInput(task.getIn()))
                        .location(new SourceMapV1(sm))
                        .build();
            }
        }
        return null;
    }

    private static Map<String, Serializable> toInput(Set<VariableMapping> in) {
        if (in == null || in.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Serializable> result = new LinkedHashMap<>();
        for (VariableMapping m : in) {
            Serializable value = null;
            if (m.getSourceValue() != null) {
                value = (Serializable)m.getSourceValue();
            } else if (m.getSourceExpression() != null) {
                value = m.getSourceExpression();
            }
            result.put(m.getTarget(), value);
        }
        return result;
    }
}
