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

import com.walmartlabs.concord.process.loader.model.Options;
import com.walmartlabs.concord.process.loader.model.ProcessDefinition;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.ByteArrayOutputStream;
import java.util.Collections;

@Named
@Singleton
public class EffectiveProcessDefinitionProcessor implements PayloadProcessor {

    private static final Logger log = LoggerFactory.getLogger(EffectiveProcessDefinitionProcessor.class);
    private static final String EFFECTIVE_FLOW_PATH = ".concord/effective.concord.yml";

    private final ProcessStateManager stateManager;

    @Inject
    public EffectiveProcessDefinitionProcessor(ProcessStateManager stateManager) {
        this.stateManager = stateManager;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        ProcessDefinition pd = payload.getHeader(Payload.PROJECT_DEFINITION);
        if (pd == null) {
            return chain.process(payload);
        }

        Options opts = Options.builder()
                .instanceId(payload.getProcessKey().getInstanceId())
                .parentInstanceId(payload.getHeader(Payload.PARENT_INSTANCE_ID))
                .configuration(payload.getHeader(Payload.CONFIGURATION, Collections.emptyMap()))
                .activeProfiles(payload.getHeader(Payload.ACTIVE_PROFILES, Collections.emptyList()))
                .build();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            pd.serialize(opts, out);
            byte[] bytes = out.toByteArray();
            if (bytes.length == 0) {
                return chain.process(payload);
            }
            stateManager.tx(tx -> {
                stateManager.deleteFile(tx, payload.getProcessKey(), EFFECTIVE_FLOW_PATH);
                stateManager.insert(tx, payload.getProcessKey(), EFFECTIVE_FLOW_PATH, bytes);
            });
        } catch (Exception e) {
            log.warn("process ['{}'] -> error: {}", payload.getProcessKey(), e.getMessage());
            throw new ProcessException(payload.getProcessKey(), "Error while processing effective concord.yml: " + e.getMessage(), e);
        }
        return chain.process(payload);
    }
}
