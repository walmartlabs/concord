package com.walmartlabs.concord.runtime.v2;

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

import com.walmartlabs.concord.runtime.v2.model.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ProcessDefinitionUtils {

    public static String getCurrentFlowName(ProcessDefinition processDefinition, Step currentStep) {
        if (currentStep == null) {
            return null;
        }

        for (Map.Entry<String, List<Step>> e : processDefinition.flows().entrySet()) {
            if (containsStep(e.getValue(), currentStep)) {
                return e.getKey();
            }
        }

        return null;
    }

    // TODO: add flow name into state local variable?
    private static boolean containsStep(List<Step> steps, Step step) {
        if (steps == null) {
            return false;
        }

        for (Step s : steps) {
            if (s == step) {
                return true;
            } else if (s instanceof IfStep) {
                boolean contains = containsStep(((IfStep) s).getThenSteps(), step)
                        || containsStep(((IfStep) s).getElseSteps(), step);
                if (contains) {
                    return true;
                }
            } else if (s instanceof SwitchStep) {
                List<Step> caseSteps = null;
                if (((SwitchStep) s).getCaseSteps() != null) {
                    caseSteps = ((SwitchStep) s).getCaseSteps().stream().flatMap(o -> o.getValue().stream()).collect(Collectors.toList());
                }
                boolean contains = containsStep(((SwitchStep) s).getDefaultSteps(), step)
                        || containsStep(caseSteps, step);
                if (contains) {
                    return true;
                }
            } else if (s instanceof GroupOfSteps) {
                return containsStep(((GroupOfSteps) s).getSteps(), step);
            } else if (s instanceof ParallelBlock) {
                return containsStep(((ParallelBlock) s).getSteps(), step);
            }
        }
        return false;
    }

    private ProcessDefinitionUtils() {
    }
}
