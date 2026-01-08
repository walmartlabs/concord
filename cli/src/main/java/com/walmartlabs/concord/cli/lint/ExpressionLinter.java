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

import com.walmartlabs.concord.runtime.model.ExpressionStep;
import com.walmartlabs.concord.runtime.model.SourceMap;
import com.walmartlabs.concord.runtime.model.Step;

import javax.el.ELException;
import java.util.Collections;
import java.util.List;

public class ExpressionLinter extends FlowElementLinter {

    public ExpressionLinter(boolean verbose) {
        super(verbose);
    }

    @Override
    protected List<LintResult> apply(Step element) {
        ExpressionStep task = (ExpressionStep) element;

        String expr = task.expression();
        notify("  Validating expression: " + expr);

        if (expr == null || expr.trim().isEmpty()) {
            String msg = "Empty or null expression";
            return Collections.singletonList(LintResult.error(element.location(), msg));
        }

        LintResult r = validate(expr, element.location());
        if (r != null) {
            return Collections.singletonList(r);
        }

        return null;
    }

    @Override
    protected boolean accepts(Step element) {
        return element instanceof ExpressionStep;
    }

    @Override
    protected String getStartMessage() {
        return "Validating expressions...";
    }

    public static LintResult validate(String expr, SourceMap sourceMap) {
        try {
            Utils.compileExpression(expr);
        } catch (ELException e) {
            return Utils.toResult(e, sourceMap, null);
        }

        return null;
    }
}
