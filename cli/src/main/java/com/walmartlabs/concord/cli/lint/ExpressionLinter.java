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

import io.takari.bpm.model.AbstractElement;
import io.takari.bpm.model.ExpressionType;
import io.takari.bpm.model.ServiceTask;
import io.takari.bpm.model.SourceMap;

import javax.el.ELException;
import java.util.Collections;
import java.util.List;

public class ExpressionLinter extends FlowElementLinter {

    public ExpressionLinter(boolean verbose) {
        super(verbose);
    }

    @Override
    protected List<LintResult> apply(AbstractElement element, SourceMap sourceMap) {
        ServiceTask task = (ServiceTask) element;

        ExpressionType type = task.getType();
        if (type != ExpressionType.SIMPLE) {
            return null;
        }

        String expr = task.getExpression();
        notify("  Validating expression: " + expr);

        if (expr == null || expr.trim().isEmpty()) {
            String msg = "Empty or null expression";
            return Collections.singletonList(LintResult.error(sourceMap, msg));
        }

        LintResult r = validate(expr, sourceMap);
        if (r != null) {
            return Collections.singletonList(r);
        }

        return null;
    }

    @Override
    protected boolean accepts(AbstractElement element) {
        return element instanceof ServiceTask;
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
