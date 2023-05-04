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
import com.walmartlabs.concord.sdk.MapUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Singleton
public class MockDefinitionProvider {

    @Inject
    public MockDefinitionProvider() {
    }

    public synchronized MockDefinition get(Context ctx, String taskName) {
        List<Map<String, Object>> mocks = ctx.variables().getList("mocks", Collections.emptyList());
        for (Iterator<Map<String, Object>> iterator = mocks.iterator(); iterator.hasNext(); ) {
            Map<String, Object> mock = iterator.next();
            String name = MapUtils.assertString(mock, "name");
            if (name.equals(taskName)) {
                iterator.remove();
                return new ObjectMapper().convertValue(mock, MockDefinition.class);
            }
        }
        return null;
    }
}
