package com.walmartlabs.concord.server.process.pipelines.processors;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessKey;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;

import javax.inject.Inject;
import javax.inject.Named;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

@Named
public class PayloadStoreProcessor implements PayloadProcessor {

    private final ObjectMapper objectMapper;
    private final ProcessStateManager stateManager;

    @Inject
    public PayloadStoreProcessor(ProcessStateManager stateManager) {
        this.objectMapper = new ObjectMapper()
                .enableDefaultTyping(ObjectMapper.DefaultTyping.JAVA_LANG_OBJECT)
                .registerModule(new GuavaModule())
                .registerModule(new Jdk8Module())
                .registerModule(new JavaTimeModule());

        this.stateManager = stateManager;
    }

    @Override
    @WithTimer
    public Payload process(Chain chain, Payload payload) {
        ProcessKey processKey = payload.getProcessKey();

        Map<String, Object> headers = payload.getHeaders().entrySet().stream()
                .filter(e -> !(e.getValue() instanceof Path))
                .filter(e -> !(e.getKey().equals(Payload.POLICY.name())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        String serializedHeaders = serialize(headers);

        stateManager.insert(processKey.getInstanceId(), processKey.getCreatedAt(), "_initial/payload.json", serializedHeaders.getBytes());

        stateManager.importPath(processKey, "_initial/attachments/", payload.getHeader(Payload.BASE_DIR), (path, basicFileAttributes) -> payload.getAttachments().containsValue(path));

        return chain.process(payload);
    }

    private String serialize(Map<String, Object> in) {
        try {
            return objectMapper.writeValueAsString(in);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
