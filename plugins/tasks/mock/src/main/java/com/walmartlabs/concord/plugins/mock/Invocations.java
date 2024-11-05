package com.walmartlabs.concord.plugins.mock;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.walmartlabs.concord.plugins.mock.matcher.ArgsMatcher;
import com.walmartlabs.concord.runtime.v2.runner.PersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.StandardOpenOption;
import java.util.List;

@Singleton
public class Invocations {

    private static final Logger log = LoggerFactory.getLogger(Invocations.class);

    private static final TypeReference<List<Invocation>> INVOCATIONS_TYPE = new TypeReference<>() {
    };

    private final PersistenceService persistenceService;
    private final ObjectMapper objectMapper;

    @Inject
    public Invocations(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
        this.objectMapper = new ObjectMapper(
                new YAMLFactory()
                        .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));
    }

    public synchronized void record(Invocation invocation) {
        String taskName = invocation.taskName();
        persistenceService.persistFile("invocations/" + taskName,
                out -> objectMapper.writeValue(out, List.of(invocation)),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public synchronized List<Invocation> find(String taskName, String methodName, Object[] args) {
        return find(taskName, methodName).stream()
                .filter(i -> ArgsMatcher.match(i.args(), args))
                .toList();
    }

    public synchronized List<Invocation> find(String taskName, String methodName) {
        var result = persistenceService.loadPersistedFile("invocations/" + taskName, in -> objectMapper.readValue(in, INVOCATIONS_TYPE));
        if (result == null) {
            return List.of();
        }
        return result.stream()
                .filter(i -> i.methodName().equals(methodName))
                .toList();
    }

    public synchronized void cleanup() {
        try {
            persistenceService.deletePersistedFile("invocations");
        } catch (IOException e) {
            log.warn("Can't cleanup invocations from state: {}", e.getMessage());
        }
    }
}
