package com.walmartlabs.concord.runtime.v1.wrapper;

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

import com.walmartlabs.concord.runtime.model.Configuration;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.MapUtils;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ConfigurationV1 implements Configuration, Serializable {

    private static final long serialVersionUID = 1L;

    private final Map<String, Object> values;

    public ConfigurationV1(Map<String, Object> values) {
        this.values = values;
    }

    @Override
    public List<String> dependencies() {
        return MapUtils.getList(values, Constants.Request.DEPENDENCIES_KEY, Collections.emptyList());
    }

    @Override
    public List<String> extraDependencies() {
        return Collections.emptyList();
    }

    @Override
    public Map<String, Object> asMap() {
        return values;
    }
}
