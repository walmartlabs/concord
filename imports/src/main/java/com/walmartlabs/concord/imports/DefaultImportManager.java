package com.walmartlabs.concord.imports;

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

import com.walmartlabs.concord.repository.Snapshot;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DefaultImportManager implements ImportManager {

    private final Map<String, ImportProcessor<Import>> processors;

    @SuppressWarnings("unchecked")
    public DefaultImportManager(List<ImportProcessor> processors) {
        this.processors = processors.stream().collect(Collectors.toMap(ImportProcessor::type, o -> o));
    }

    @Override
    public List<Snapshot> process(Imports imports, Path dest) throws Exception {
        List<Snapshot> result = new ArrayList<>();

        List<Import> items = imports.items();
        if (items == null || items.isEmpty()) {
            return result;
        }

        for (Import i : items) {
            Snapshot s = assertProcessor(i.type()).process(i, dest);
            result.add(s);
        }

        return result;
    }

    private ImportProcessor<Import> assertProcessor(String type) {
        ImportProcessor<Import> p = processors.get(type);
        if (p != null) {
            return p;
        }
        throw new RuntimeException("Unknown import type: '" + type + "'");
    }
}
