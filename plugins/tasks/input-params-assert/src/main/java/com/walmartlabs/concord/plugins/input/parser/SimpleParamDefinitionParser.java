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

import java.util.Arrays;

public class SimpleParamDefinitionParser {

    public static ParamDefinition parse(String str) {
        int pos = str.indexOf(",");
        String paramTypeStr;
        if (pos > 0) {
            paramTypeStr = str.substring(0, pos);
            str = str.substring(pos + 1);
        } else {
            paramTypeStr = str;
            str = null;
        }

        ParamType paramType = toParamType(paramTypeStr);
        boolean isArray = paramTypeStr.trim().endsWith("[]");

        boolean optional = false;
        if (str != null) {
            pos = str.indexOf(",");
            if (pos > 0) {
                String optionalOrMandatory = str.substring(0, pos).trim();
                if ("optional".equalsIgnoreCase(optionalOrMandatory)) {
                    optional = true;
                    str = str.substring(pos + 1);
                } else if ("mandatory".equalsIgnoreCase(optionalOrMandatory)) {
                    optional = false;
                    str = str.substring(pos + 1);
                }
            }
        }

        String description = null;
        if (str != null) {
            description = str.trim();
        }
        return ParamDefinition.builder()
                .optional(optional)
                .description(description)
                .type(paramType)
                .isSimpleArray(isArray)
                .build();
    }

    private static ParamType toParamType(String type) {
        String normalized;
        if (type.endsWith("[]")) {
            normalized = type.substring(0, type.length() - 2).trim();
        } else {
            normalized = type.trim();
        }

        for (ParamType t : ParamType.values()) {
            if (t.name().equalsIgnoreCase(normalized)) {
                return t;
            } else if (t.getAliases().stream().anyMatch(a -> a.equalsIgnoreCase(normalized))) {
                return t;
            }
        }
        throw new RuntimeException("Unknown param type: '" + type + "'. Available types: " + Arrays.toString(ParamType.values()));
    }
}
