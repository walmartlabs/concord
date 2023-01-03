package com.walmartlabs.concord.imports;

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

import com.walmartlabs.concord.repository.Snapshot;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultImportManager implements ImportManager {

    private final Map<String, ImportProcessor<Import>> processors;
    private final Set<String> disabledProcessors;

    @SuppressWarnings({"unchecked", "rawtypes"})
    public DefaultImportManager(List<ImportProcessor> processors, Set<String> disabledProcessors) {
        this.processors = processors.stream().collect(Collectors.toMap(ImportProcessor::type, o -> o));
        this.disabledProcessors = disabledProcessors;
    }

    @Override
    public List<Snapshot> process(Imports imports, Path dest, ImportsListener listener) throws Exception {
        if (listener == null) {
            listener = ImportsListener.NOP_LISTENER;
        }

        List<Snapshot> result = new ArrayList<>();

        List<Import> items = imports.items();
        if (items == null || items.isEmpty()) {
            return result;
        }

        listener.onStart(items);

        for (Import i : items) {
            listener.beforeImport(i);
            Snapshot s;
            try {
                s = assertProcessor(i.type()).process(i, dest);
            } catch (Exception e) {
                throw new ImportProcessingException(i, e);
            }
            listener.afterImport(i);
            result.add(s);
        }

        listener.onEnd(items);

        return result;
    }

    private ImportProcessor<Import> assertProcessor(String type) {
        if (disabledProcessors.contains(type)) {
            throw new RuntimeException("Disabled import type: " + type);
        }

        ImportProcessor<Import> p = processors.get(type);
        if (p != null) {
            return p;
        }
        throw new RuntimeException("Unknown import type: " + type);
    }
}
