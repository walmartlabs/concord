package com.walmartlabs.concord.cli.lint;

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

import com.walmartlabs.concord.runtime.model.SourceMap;
import com.walmartlabs.concord.runtime.model.Step;
import com.walmartlabs.concord.runtime.model.TaskCallStep;

import javax.el.ELException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class TaskCallLinter extends FlowElementLinter {

    public TaskCallLinter(boolean verbose) {
        super(verbose);
    }

    @Override
    protected List<LintResult> apply(Step element) {
        List<LintResult> results = new ArrayList<>();

        TaskCallStep task = (TaskCallStep) element;

        String expr = task.name();
        notify("  Validating task call: " + expr);

        LintResult r = ExpressionLinter.validate(expr, element.location());
        if (r != null) {
            results.add(r);
        }

        Map<String, Serializable> inVars = task.input();
        for (Map.Entry<String, Serializable> e : inVars.entrySet()) {
            LintResult lr = validateArgument(e.getKey(), e.getValue(), element.location());
            if (lr != null) {
                results.add(lr);
            }
        }

        return results;
    }

    private LintResult validateArgument(String paramName, Object value, SourceMap sourceMap) {
        if (value != null) {
            return validateValue(paramName, value, sourceMap);
        }

        return null;
    }

    private LintResult validateValue(String paramName, Object value, SourceMap sourceMap) {
        if (value instanceof String) {
            String s = (String) value;
            if (s.contains("${")) {
                return validateExpression(paramName, s, sourceMap);
            }
        } else if (value instanceof Collection) {
            Collection c = (Collection) value;
            for (Object vv : c) {
                LintResult r = validateValue(paramName, vv, sourceMap);
                if (r != null) {
                    return r;
                }
            }
        } else if (value instanceof Map) {
            Map m = (Map) value;
            for (Object vv : m.values()) {
                LintResult r = validateValue(paramName, vv, sourceMap);
                if (r != null) {
                    return r;
                }
            }
        }

        return null;
    }

    private LintResult validateExpression(String paramName, String expr, SourceMap sourceMap) {
        try {
            Utils.compileExpression(expr);
        } catch (ELException e) {
            return Utils.toResult(e, sourceMap, "Invalid expression in task arguments: \"" + expr + "\" in IN " + paramName);
        }

        return null;
    }

    @Override
    protected boolean accepts(Step element) {
        return element instanceof TaskCallStep;
    }

    @Override
    protected String getStartMessage() {
        return "Validating task calls...";
    }
}
