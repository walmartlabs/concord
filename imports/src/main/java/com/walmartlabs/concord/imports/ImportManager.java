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
import com.walmartlabs.concord.server.queueclient.message.ImportEntry;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ImportManager {

    private final Map<String, ImportProcessor<ImportEntry>> processors;

    @SuppressWarnings("unchecked")
    public ImportManager(List<ImportProcessor> processors) {
        this.processors = processors.stream().collect(Collectors.toMap(ImportProcessor::type, o -> o));
    }

    public List<Snapshot> process(List<ImportEntry> imports, Path workDir) throws Exception {
        List<Snapshot> result = new ArrayList<>();
        for (ImportEntry i : imports) {
            Snapshot s = assertProcessor(i.type()).process(i, workDir);
            result.add(s);
        }
        return result;
    }

    private ImportProcessor<ImportEntry> assertProcessor(String type) {
        ImportProcessor<ImportEntry> p = processors.get(type);
        if (p != null) {
            return p;
        }
        throw new RuntimeException("Unknown import type: '" + type + "'");
    }
}
