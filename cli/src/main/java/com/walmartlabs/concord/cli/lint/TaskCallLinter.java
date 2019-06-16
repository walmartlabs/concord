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

import io.takari.bpm.model.*;

import javax.el.ELException;
import java.util.*;

public class TaskCallLinter extends FlowElementLinter {

    public TaskCallLinter(boolean verbose) {
        super(verbose);
    }

    @Override
    protected List<LintResult> apply(AbstractElement element, SourceMap sourceMap) {
        List<LintResult> results = new ArrayList<>();

        ServiceTask task = (ServiceTask) element;

        ExpressionType type = task.getType();
        if (type != ExpressionType.DELEGATE) {
            return null;
        }

        String expr = task.getExpression();
        notify("  Validating task call: " + expr);

        LintResult r = ExpressionLinter.validate(expr, sourceMap);
        if (r != null) {
            results.add(r);
        }

        Set<VariableMapping> inVars = task.getIn();
        if (inVars != null) {
            for (VariableMapping m : inVars) {
                LintResult lr = validateArgument(m, sourceMap);
                if (lr != null) {
                    results.add(lr);
                }
            }
        }

        return results;
    }

    private LintResult validateArgument(VariableMapping mapping, SourceMap sourceMap) {
        Object v = mapping.getSourceValue();
        if (v != null) {
            return validateValue(v, mapping, sourceMap);
        }

        String expr = mapping.getSourceExpression();
        if (expr == null) {
            return null;
        }

        return validateExpression(expr, mapping, sourceMap);
    }

    private LintResult validateValue(Object v, VariableMapping mapping, SourceMap sourceMap) {
        if (v instanceof String) {
            String s = (String) v;
            if (s.contains("${")) {
                return validateExpression(s, mapping, sourceMap);
            }
        } else if (v instanceof Collection) {
            Collection c = (Collection) v;
            for (Object vv : c) {
                LintResult r = validateValue(vv, mapping, sourceMap);
                if (r != null) {
                    return r;
                }
            }
        } else if (v instanceof Map) {
            Map m = (Map) v;
            for (Object vv : m.values()) {
                LintResult r = validateValue(vv, mapping, sourceMap);
                if (r != null) {
                    return r;
                }
            }
        }

        return null;
    }

    private LintResult validateExpression(String expr, VariableMapping mapping, SourceMap sourceMap) {
        try {
            Utils.compileExpression(expr);
        } catch (ELException e) {
            return Utils.toResult(e, sourceMap, "Invalid expression in task arguments: \"" + expr + "\" in IN " + mapping);
        }

        return null;
    }

    @Override
    protected boolean accepts(AbstractElement element) {
        return element instanceof ServiceTask;
    }

    @Override
    protected String getStartMessage() {
        return "Validating task calls...";
    }
}
