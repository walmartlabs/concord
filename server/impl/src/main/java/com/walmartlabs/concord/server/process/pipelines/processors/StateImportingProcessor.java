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

import com.walmartlabs.concord.repository.Snapshot;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessKey;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

@Named
public class StateImportingProcessor implements PayloadProcessor {

    private static final Logger log = LoggerFactory.getLogger(StateImportingProcessor.class);

    private final ProcessStateManager stateManager;

    @Inject
    public StateImportingProcessor(ProcessStateManager stateManager) {

        this.stateManager = stateManager;
    }

    @Override
    @WithTimer
    public Payload process(Chain chain, Payload payload) {
        ProcessKey processKey = payload.getProcessKey();
        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);
        Snapshot snapshot = payload.getHeader(RepositoryProcessor.REPOSITORY_SNAPSHOT);
        stateManager.replacePath(processKey, workspace, (p, attrs) -> filter(p, attrs, snapshot));

        return chain.process(payload);
    }

    private boolean filter(Path p, BasicFileAttributes attrs, Snapshot snapshot) {
        if (p.isAbsolute()) {
            log.warn("filter ['{}'] -> can't filter absolute paths", p);
            return true;
        }

        if (snapshot != null) {
            return snapshot.isModified(p, attrs);
        }

        return true;
    }
}
