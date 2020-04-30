package com.walmartlabs.concord.process.loader.v2;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.walmartlabs.concord.process.loader.model.Configuration;
import com.walmartlabs.concord.runtime.v2.model.ProcessConfiguration;

import java.io.Serializable;
import java.util.Map;

public class ConfigurationV2 implements Configuration, Serializable {

    private static final long serialVersionUID = 1L;

    private final Map<String, Object> values;

    @SuppressWarnings("unchecked")
    public ConfigurationV2(ProcessConfiguration cfg) {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new Jdk8Module());
        om.registerModule(new JavaTimeModule());
        this.values = om.convertValue(cfg, Map.class);
    }

    @Override
    public Map<String, Object> asMap() {
        return values;
    }
}
