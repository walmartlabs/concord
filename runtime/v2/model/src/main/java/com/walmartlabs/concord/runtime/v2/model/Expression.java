package com.walmartlabs.concord.runtime.v2.model;

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

import com.walmartlabs.concord.runtime.model.Location;

public class Expression extends AbstractStep<ExpressionOptions> {

    public static Expression shortForm(Location location, String expr) {
        return new Expression(location, expr, ExpressionOptions.builder().build());
    }

    private static final long serialVersionUID = 1L;

    private final String expr;

    public Expression(Location location, String expr, ExpressionOptions options) {
        super(location, options);
        this.expr = expr;
    }

    public String getExpr() {
        return expr;
    }

    @Override
    public String toString() {
        return "Expression{" +
                "expr='" + expr + '\'' +
                "}";
    }
}
