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
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessSecurityContext;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.security.SecurityUtils;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Saves the current {@link Payload} data as a workspace file.
 * <p/>
 * It will be restored by {@link com.walmartlabs.concord.server.process.pipelines.EnqueueProcessPipeline},
 * when the process transitions from NEW to ENQUEUED.
 */
public class PayloadStoreProcessor implements PayloadProcessor {

    private static final Set<String> EXCLUDED_HEADERS = new HashSet<>(Arrays.asList(Payload.POLICY.name(), Payload.REPOSITORY_SNAPSHOT.name()));

    private final ObjectMapper objectMapper;
    private final ProcessStateManager stateManager;
    private final ProcessSecurityContext securityContext;

    @Inject
    public PayloadStoreProcessor(ProcessStateManager stateManager,
                                 ProcessSecurityContext securityContext) {
        this.objectMapper = new ObjectMapper()
                .enableDefaultTyping(ObjectMapper.DefaultTyping.JAVA_LANG_OBJECT)
                .registerModule(new GuavaModule())
                .registerModule(new Jdk8Module())
                .registerModule(new JavaTimeModule());

        this.stateManager = stateManager;
        this.securityContext = securityContext;
    }

    @Override
    @WithTimer
    public Payload process(Chain chain, Payload payload) {
        ProcessKey processKey = payload.getProcessKey();

        // remove things that shouldn't be in the serialized payload
        Map<String, Object> headers = payload.getHeaders().entrySet().stream()
                .filter(e -> !(e.getValue() instanceof Path))
                .filter(e -> !EXCLUDED_HEADERS.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        String serializedHeaders = serialize(headers);

        stateManager.tx(tx -> {
            stateManager.insertInitial(tx, processKey, "payload.json", serializedHeaders.getBytes());
            stateManager.insertInitial(tx, processKey, "initiator", securityContext.serializePrincipals(SecurityUtils.getSubject().getPrincipals()));
            stateManager.importPathInitial(tx, processKey, "attachments/", payload.getHeader(Payload.BASE_DIR), (path, basicFileAttributes) -> payload.getAttachments().containsValue(path));
        });

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
