package com.walmartlabs.concord.runtime.v2.runner.el;

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

import java.util.*;

public class LazyEvalList extends AbstractList<Object> {

    private final LazyExpressionEvaluator evaluator;
    private final LazyEvalContext context;
    private final Set<Integer> inflightKeys = new HashSet<>();
    private final List<?> originalValues;
    private final Map<Integer, Object> evaluatedValues = new HashMap<>();

    public LazyEvalList(LazyExpressionEvaluator evaluator, LazyEvalContext context, List<?> src) {
        this.evaluator = evaluator;
        this.context = context;
        this.originalValues = src;
    }

    @Override
    public Object get(int index) {
        return evalValue(index);
    }

    @Override
    public int size() {
        return originalValues.size();
    }

    private Object evalValue(int index) {
        if (evaluatedValues.containsKey(index)) {
            return evaluatedValues.get(index);
        }

        try {
            boolean newKey = inflightKeys.add(index);
            if (!newKey) {
                throw new RuntimeException("Element with index='" + index + "' already in evaluation");
            }

            Object originalValue = originalValues.get(index);

            Object result = evaluator.evalValue(context, originalValue, Object.class);

            evaluatedValues.put(index, result);

            return result;
        } finally {
            inflightKeys.remove(index);
        }
    }

    @Override
    public String toString() {
        return "LazyEvalList{" +
                "inflightKeys=" + inflightKeys +
                ", originalValues=" + originalValues +
                ", evaluatedValues=" + evaluatedValues +
                '}';
    }
}
