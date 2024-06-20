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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.plugins.input.parser.CommentParser;
import com.walmartlabs.concord.plugins.input.parser.CommentsGrabber;
import com.walmartlabs.concord.runtime.v2.ProjectLoadListener;
import com.walmartlabs.concord.runtime.v2.runner.PersistenceService;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class ConcordYamlProcessor implements ProjectLoadListener {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final PersistenceService persistenceService;

    private static final FlowCallSchemaGenerator schemaGenerator = new FlowCallSchemaGenerator(new CommentsGrabber(), new CommentParser());

    private final Map<String, Object> definitions = new LinkedHashMap<>();

    @Inject
    public ConcordYamlProcessor(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @Override
    public void afterFlowDefinitionLoaded(Path filename) {
        if (schemaExists()) {
            return;
        }

        definitions.putAll(schemaGenerator.generate(filename));
    }

    @Override
    public void afterProjectLoaded() {
        if (schemaExists()) {
            return;
        }

        persistenceService.persistFile(InputParamsDefinitionProvider.DEFAULT_SCHEMA_FILENAME, out -> mapper.writeValue(out, definitions));
    }

    private boolean schemaExists() {
        return persistenceService.loadPersistedFile(InputParamsDefinitionProvider.DEFAULT_SCHEMA_FILENAME, inputStream -> true) != null;
    }
}
