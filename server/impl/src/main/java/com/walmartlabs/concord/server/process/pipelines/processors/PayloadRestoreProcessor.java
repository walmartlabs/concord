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
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessKey;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Named
public class PayloadRestoreProcessor implements PayloadProcessor {

    private final ObjectMapper objectMapper;
    private final ProcessStateManager stateManager;

    @Inject
    public PayloadRestoreProcessor(ProcessStateManager stateManager) {
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

        Map<String, Object> headers = stateManager.get(processKey, "_initial/payload.json", inputStream -> {
            Map<String, Object> result = deserialize(inputStream);
            return Optional.ofNullable(result);
        }).orElseThrow(() -> new RuntimeException("Initial state not found"));

        payload = payload.putHeaders(headers);

        Path baseDir = payload.getHeader(Payload.BASE_DIR);

        ProcessStateManager.ItemConsumer cp = ProcessStateManager.copyTo(baseDir);
        Map<String, Path> attachments = new HashMap<>();
        stateManager.exportDirectory(processKey, "_initial/attachments/", (name, unixMode, src) -> {
            cp.accept(name, unixMode, src);
            attachments.put(name, baseDir.resolve(name));
        });
        payload = payload.putAttachments(attachments);


        return chain.process(payload);
    }

    private Map<String, Object> deserialize(InputStream is) {
        try {
            return objectMapper.readValue(is, ConcordObjectMapper.MAP_TYPE);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
