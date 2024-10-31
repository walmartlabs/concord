package com.walmartlabs.concord.plugins.mock;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.plugins.mock.matcher.ArgsMatcher;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.UserDefinedException;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;

import javax.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

@Singleton
public class MockDefinitionProvider {

    public MockDefinition find(Context ctx, String taskName, Variables input) {
        return findMockDefinitions(ctx, mock ->
                taskName.equals(mock.task()) && ArgsMatcher.match(input.toMap(), mock.input()));
    }

    public MockDefinition find(Context ctx, String taskName, String method, Object[] params) {
        return findMockDefinitions(ctx, mock ->
                taskName.equals(mock.task()) && method.equals(mock.method()) && ArgsMatcher.match(params, mock.args()));
    }

    public boolean isTaskMocked(Context ctx, String taskName) {
        return mocks(ctx).anyMatch(mock -> taskName.equals(mock.task()));
    }

    private static MockDefinition findMockDefinitions(Context ctx, Predicate<MockDefinition> predicate) {
        var candidates = mocks(ctx).filter(predicate).toList();
        if (candidates.isEmpty()) {
            return null;
        } else if (candidates.size() == 1) {
            return candidates.get(0);
        }
        throw new UserDefinedException("Too many mocks: " + candidates);
    }

    @SuppressWarnings("unchecked")
    private static Stream<MockDefinition> mocks(Context ctx) {
        return ctx.variables().getList("mocks", List.of()).stream()
                .map(m -> new MockDefinition((Map<String, Object>) m));
    }
}
