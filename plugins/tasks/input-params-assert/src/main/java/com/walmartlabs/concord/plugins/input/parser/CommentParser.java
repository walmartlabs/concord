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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.util.*;

public class CommentParser {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {
    };

    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    private static final Set<String> keys = new HashSet<>(Arrays.asList("in:", "out:"));

    @SuppressWarnings("unchecked")
    public FlowDefinition parse(List<String> commentLines) {
        Map<String, Object> fields = parseFields(commentLines);
        return FlowDefinition.builder()
                .description((String)(fields.get("description")))
                .in(convert((Map<String, String>)fields.get("in")))
                .out(convert((Map<String, String>)fields.get("out")))
                .build();
    }

    private static boolean isHeader(String line) {
        return line.trim().startsWith("##");
    }

    private static Map<String, ParamDefinition> convert(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return Collections.emptyMap();
        }

        return ParamDefinitionParser.parse(params);
    }

    public Map<String, Object> parseFields(List<String> commentLines) {
        if (commentLines.isEmpty()) {
            return Collections.emptyMap();
        }

        if (!isHeader(commentLines.get(0))) {
            throw new RuntimeException("No header");
        }

        int keyIndex = findKey(commentLines, keys);
        if (keyIndex == -1) {
            throw new RuntimeException("No in/out defined");
        }

        StringBuilder description = new StringBuilder();
        for (int i = 0; i < keyIndex; i++) {
            String line = trimStartChar(commentLines.get(i).trim(), '#').trim();
            if (!line.isEmpty()) {
                description.append(line);
            }
        }

        StringBuilder inOut = new StringBuilder();
        for (int i = keyIndex; i < commentLines.size(); i++) {
            String line = trimStartChar(commentLines.get(i).trim(), '#');
            inOut.append(line).append('\n');
        }

        Map<String, Object> fields = readMap(inOut.toString());
        if (description.length() > 0) {
            fields.put("description", description.toString());
        }
        return fields;
    }

    private static Map<String, Object> readMap(String content) {
        try {
            return mapper.readValue(content, MAP_TYPE);
        } catch (Exception e) {
            throw new RuntimeException("Error reading '" + content + "': " + e.getMessage());
        }
    }

    private static int findKey(List<String> commentLines, Set<String> keys) {
        for (int i = 0; i< commentLines.size(); i++) {
            String line = trimStartChar(commentLines.get(i).trim(), '#').trim();
            if (keys.contains(line)) {
                return i;
            }
        }
        return -1;
    }

    private static String trimStartChar(String str, char ch) {
        int startIndex = 0;
        int length = str.length();
        while (startIndex < length && str.charAt(startIndex) == ch) {
            startIndex++;
        }
        return str.substring(startIndex);
    }
}
