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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.walmartlabs.concord.common.AllowNulls;
import com.walmartlabs.concord.sdk.MapUtils;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MockDefinition {

    private final Map<String, Object> definition;

    public MockDefinition(Map<String, Object> definition) {
        this.definition = definition;
    }

    public String name() {
        return MapUtils.assertString(definition, "name");
    }

    public Map<String, Object> input() {
        return MapUtils.getMap(definition, "in", Map.of());
    }

    public Map<String, Object> out() {
        return MapUtils.getMap(definition, "out", Map.of());
    }

    public String method() {
        return MapUtils.getString(definition, "method");
    }

    public List<Object> args() {
        return MapUtils.getList(definition, "args", List.of());
    }

    public Serializable result() {
        return MapUtils.get(definition, "result", Serializable.class);
    }

    public String throwError() {
        return MapUtils.getString(definition, "throwError");
    }
}
