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
import com.walmartlabs.concord.server.process.ProcessSecurityContext;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.security.SecurityUtils;
import org.apache.shiro.subject.PrincipalCollection;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class PayloadRestoreProcessor implements PayloadProcessor {

    private final ObjectMapper objectMapper;
    private final ProcessStateManager stateManager;
    private final ProcessSecurityContext securityContext;

    @Inject
    public PayloadRestoreProcessor(ProcessStateManager stateManager,
                                   ProcessSecurityContext securityContext) {
        this.securityContext = securityContext;
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

        Map<String, Object> headers = stateManager.getInitial(processKey, "payload.json", inputStream -> {
            Map<String, Object> result = deserialize(inputStream);
            return Optional.ofNullable(result);
        }).orElseThrow(() -> new ConcordApplicationException("Initial state not found", Response.Status.INTERNAL_SERVER_ERROR));

        payload = payload.putHeaders(headers);

        Path baseDir = payload.getHeader(Payload.BASE_DIR);

        ProcessStateManager.ItemConsumer cp = ProcessStateManager.copyTo(baseDir);
        Map<String, Path> attachments = new HashMap<>();
        stateManager.exportDirectoryInitial(processKey, "attachments/", (name, unixMode, src) -> {
            cp.accept(name, unixMode, src);
            attachments.put(name, baseDir.resolve(name));
        });
        payload = payload.putAttachments(attachments);

        PrincipalCollection principals = stateManager.getInitial(processKey, "initiator", SecurityUtils::deserialize)
                .orElseThrow(() -> new ConcordApplicationException("Process initiator not found", Response.Status.INTERNAL_SERVER_ERROR));

        securityContext.storeSubject(processKey, principals);

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
