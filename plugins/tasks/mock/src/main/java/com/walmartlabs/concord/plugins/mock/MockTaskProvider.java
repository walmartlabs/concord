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


import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskProvider;
import org.eclipse.sisu.Priority;

import javax.inject.Inject;
import java.util.Collections;
import java.util.Set;

@Priority(-1)
public class MockTaskProvider implements TaskProvider {

    private final MockDefinitionProvider mockDefinitionProvider;

    @Inject
    public MockTaskProvider(MockDefinitionProvider mockDefinitionProvider) {
        this.mockDefinitionProvider = mockDefinitionProvider;
    }

    @Override
    public Task createTask(Context ctx, String key) {
        MockDefinition def = mockDefinitionProvider.get(ctx, key);
        if (def == null) {
            return null;
        }

        return new MockTask(ctx.execution().state(), def);
    }

    @Override
    public boolean hasTask(String key) {
        return false;
    }

    @Override
    public Set<String> names() {
        return Collections.emptySet();
    }
}
