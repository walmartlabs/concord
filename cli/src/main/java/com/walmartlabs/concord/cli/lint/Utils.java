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

import javax.el.ELContext;
import javax.el.ELException;
import javax.el.ExpressionFactory;
import javax.el.StandardELContext;

public final class Utils {

    public static void compileExpression(String expr) {
        ExpressionFactory ef = ExpressionFactory.newInstance();
        ELContext ctx = new StandardELContext(ef);
        ef.createValueExpression(ctx, expr, Object.class);
    }

    public static LintResult toResult(ELException e, SourceMap sm, String message) {
        // make EL exceptions a bit more compact
        String error = e.getCause().getMessage().replaceAll("\n", "").replaceAll(" {4}", " ");
        return LintResult.error(sm, (message != null ? message + " " + error : error));
    }

    private Utils() {
    }
}
