package com.walmartlabs.concord.project.yaml;

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
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.walmartlabs.concord.imports.Import;
import com.walmartlabs.concord.imports.Imports;
import com.walmartlabs.concord.project.yaml.converter.StepConverter;
import com.walmartlabs.concord.project.yaml.model.YamlImport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class YamlImportConverter {

    private static final ObjectMapper objectMapper = createMapper();

    @SuppressWarnings("unchecked")
    public static Imports convertImports(List<YamlImport> imports) throws YamlConverterException {
        if (imports == null || imports.isEmpty()) {
            return null;
        }

        List<Import> result = new ArrayList<>();
        for (YamlImport i : imports) {
            Map<String, Object> opts = (Map<String, Object>) StepConverter.deepConvert(i.getOptions());

            Map<String, Object> typedOpts = new HashMap<>(opts);
            typedOpts.put("type", i.getType());

            try {
                result.add(objectMapper.convertValue(typedOpts, Import.class));
            } catch (Exception e) {
                error("Error parsing import definition: " + e.getMessage(), i);
            }
        }

        return Imports.of(result);
    }

    private static void error(String message, YamlImport yamlImport) throws YamlConverterException {
        throw new YamlConverterException(message + " @ " + yamlImport.getLocation());
    }

    private static ObjectMapper createMapper() {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new GuavaModule());
        om.registerModule(new Jdk8Module());
        return om;
    }

    private YamlImportConverter() {
    }
}
