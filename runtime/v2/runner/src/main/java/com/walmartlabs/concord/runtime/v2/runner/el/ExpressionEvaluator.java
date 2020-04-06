package com.walmartlabs.concord.runtime.v2.runner.el;

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

import com.walmartlabs.concord.runtime.v2.sdk.Context;

import java.util.List;
import java.util.Map;

public interface ExpressionEvaluator {

    /**
     * Non-recursively evaluates the specified value as an expression.
     * </p>
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
    <T> T eval(Context ctx, Object value, Class<T> expectedType);

    /**
     * Same as {@link #eval(Context, Object, Class)}, but allows assigning
     * the result to a generic Map without unchecked casts.
     */
    <K, V> Map<K, V> evalAsMap(Context ctx, Object value);

    /**
     * Same as {@link #eval(Context, Object, Class)}, but allows assigning
     * the result to a generic List without unchecked casts.
     */
    <T> List<T> evalAsList(Context ctx, Object value);
}
