package com.walmartlabs.concord.runtime.v2.sdk;

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

import java.util.List;
import java.util.Map;

public interface ExpressionEvaluator {

    /**
     * Non-recursively evaluates the specified value as an expression.
     *
     * For example:
     * <pre>{@code
     *  // returns a string with ${name} replaced as its value
     *  eval(ctx, "Hello, ${name}", String.class);
     *
     *  List<String> items = Arrays.asList("Hello, ${name}", "Bye, ${name}");
     *  // returns a list where each element is replaced with the evaluated value
     *  eval(ctx, items, List.class);
     * }</pre>
     */
    <T> T eval(EvalContext ctx, Object value, Class<T> expectedType);

    /**
     * Same as {@link #eval(EvalContext, Object, Class)}, but allows assigning
     * the result to a generic Map without unchecked casts.
     */
    @SuppressWarnings("unchecked")
    default <K, V> Map<K, V> evalAsMap(EvalContext ctx, Object value) {
        return eval(ctx, value, Map.class);
    }

    /**
     * Same as {@link #eval(EvalContext, Object, Class)}, but allows assigning
     * the result to a generic List without unchecked casts.
     */
    @SuppressWarnings("unchecked")
    default <T> List<T> evalAsList(EvalContext ctx, Object value) {
        return eval(ctx, value, List.class);
    }
}
