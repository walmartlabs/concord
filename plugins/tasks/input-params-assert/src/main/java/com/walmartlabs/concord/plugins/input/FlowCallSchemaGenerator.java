package com.walmartlabs.concord.plugins.input;

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

import com.walmartlabs.concord.plugins.input.parser.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;

public class FlowCallSchemaGenerator {

    private static final Logger log = LoggerFactory.getLogger(FlowCallSchemaGenerator.class);

    private final CommentsGrabber grabber;
    private final CommentParser parser;

    public FlowCallSchemaGenerator(CommentsGrabber commentsGrabber, CommentParser parser) {
        this.grabber = commentsGrabber;
        this.parser = parser;
    }

    public Map<String, Object> generate(Path concordYaml) {
        Map<String, List<String>> comments = grabber.grab(concordYaml);

        Map<String, Object> schema = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : comments.entrySet()) {
            try {
                FlowDefinition fd = parser.parse(e.getValue());

                Map<String, Object> flowSchema = new LinkedHashMap<>();
                if (fd.description() != null) {
                    flowSchema.put("title", fd.description());
                }

                for (Map.Entry<String, ParamDefinition> in : fd.in().entrySet()) {
                    set(flowSchema, in.getKey(), in.getValue());
                }

                schema.put(e.getKey(), flowSchema);
            } catch (Exception err) {
                log.warn("error parsing comment in '" + concordYaml + "' for flow '" + e.getKey() + "': " + err.getMessage());
            }
        }
        return schema;
    }

    @SuppressWarnings("unchecked")
    private static void set(Map<String, Object> result, String key, ParamDefinition value) {
        Map<String, Object> current = result;
        String[] path = key.split("\\.");
        for (int i = 0, pathLength = path.length; i < pathLength - 1; i++) {
            String p = path[i];

            if (isArray(current)) {
                current = (Map<String, Object>) current.computeIfAbsent("items", k -> new LinkedHashMap<>());
            }
            current.putIfAbsent("type", ParamType.OBJECT.name().toLowerCase());

            Map<String, Object> properties = (Map<String, Object>) current.computeIfAbsent("properties", k -> new LinkedHashMap<>());
            current = (Map<String, Object>) properties.computeIfAbsent(p, k -> new LinkedHashMap<>());
        }

        if (isArray(current)) {
            current = (Map<String, Object>) current.computeIfAbsent("items", k -> new LinkedHashMap<>());
        }
        current.putIfAbsent("type", ParamType.OBJECT.name().toLowerCase());

        Map<String, Object> object = new LinkedHashMap<>();
        if (value.isSimpleArray()) {
            object.put("type", ParamType.ARRAY.name().toLowerCase());
            object.put("items", new LinkedHashMap<>(Collections.singletonMap("type", value.type().name().toLowerCase())));
        } else {
            object.put("type", value.type().name().toLowerCase());
        }

        if (value.description() != null) {
            object.put("description", value.description());
        }

        if (!value.optional()) {
            List<String> required = (List<String>) current.computeIfAbsent("required", k -> new ArrayList<>());
            required.add(path[path.length - 1]);
        }

        Map<String, Object> properties = (Map<String, Object>) current.computeIfAbsent("properties", k -> new LinkedHashMap<>());
        properties.put(path[path.length - 1], object);
    }

    private static boolean isArray(Map<String, Object> current) {
        return ParamType.ARRAY.name().equalsIgnoreCase((String)current.get("type"));
    }
}
