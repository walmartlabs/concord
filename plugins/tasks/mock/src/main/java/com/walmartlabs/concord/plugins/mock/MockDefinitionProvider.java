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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.UserDefinedException;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;
import com.walmartlabs.concord.sdk.MapUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

@Singleton
public class MockDefinitionProvider {

    private final ObjectMapper objectMapper;

    @Inject
    public MockDefinitionProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public MockDefinition find(Context ctx, String taskName, Variables input) {
        List<Map<String, Object>> mocks = mocks(ctx);
        List<MockDefinition> candidates = new ArrayList<>();
        for (Map<String, Object> mock : mocks) {
            String name = MapUtils.assertString(mock, "name");
            Map<String, Object> in = MapUtils.getMap(mock, "in", Map.of());
            if (taskName.equals(name) && ArgsMatcher.match(input.toMap(), in)) {
                candidates.add(objectMapper.convertValue(mock, MockDefinition.class));
            }
        }

        if (candidates.isEmpty()) {
            return null;
        } else if (candidates.size() == 1) {
            return candidates.get(0);
        }

        throw new UserDefinedException("Too many mocks: " + candidates);
    }

    public MockDefinition find(Context ctx, String taskName, String method, Object[] params) {
        List<Map<String, Object>> mocks = mocks(ctx);
        List<MockDefinition> candidates = new ArrayList<>();
        for (Map<String, Object> mock : mocks) {
            String name = MapUtils.assertString(mock, "name");
            List<Object> args = MapUtils.getList(mock, "args", List.of());
            String expectedMethod = MapUtils.getString(mock, "method");
            if (taskName.equals(name) && method.equals(expectedMethod) && ArgsMatcher.match(args, Arrays.asList(params))) {
                candidates.add(objectMapper.convertValue(mock, MockDefinition.class));
            }
        }

        if (candidates.isEmpty()) {
            return null;
        } else if (candidates.size() == 1) {
            return candidates.get(0);
        }

        throw new UserDefinedException("Too many mocks: " + candidates);
    }

    public boolean isTaskMocked(Context ctx, String taskName) {
        for (Map<String, Object> mock : mocks(ctx)) {
            String name = MapUtils.assertString(mock, "name");
            if (taskName.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static List<Map<String, Object>> mocks(Context ctx) {
        return ctx.variables().getList("mocks", Collections.emptyList());
    }
}
