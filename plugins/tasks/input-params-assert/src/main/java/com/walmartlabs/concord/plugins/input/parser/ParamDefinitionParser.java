package com.walmartlabs.concord.plugins.input.parser;

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

import java.util.LinkedHashMap;
import java.util.Map;

public class ParamDefinitionParser {

    public static Map<String, ParamDefinition> parse(Map<String, String> definition) {
        Map<String, ParamDefinition> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : definition.entrySet()) {
            result.put(e.getKey(), SimpleParamDefinitionParser.parse(e.getValue()));
        }
        return result;
    }
}
