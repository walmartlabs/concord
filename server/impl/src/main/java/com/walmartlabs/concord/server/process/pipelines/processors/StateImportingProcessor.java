package com.walmartlabs.concord.server.process.pipelines.processors;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

@Named
public class StateImportingProcessor implements PayloadProcessor {

    private static final Logger log = LoggerFactory.getLogger(StateImportingProcessor.class);

    private final ProcessStateManager stateManager;

    @Inject
    public StateImportingProcessor(ProcessStateManager stateManager) {
        this.stateManager = stateManager;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        UUID instanceId = payload.getInstanceId();
        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);

        stateManager.transaction(tx -> {
            stateManager.delete(tx, instanceId);
            stateManager.importPath(tx, instanceId, workspace, StateImportingProcessor::filter);
        });

        Path dir = payload.getHeader(Payload.BASE_DIR, workspace);
        try {
            IOUtils.deleteRecursively(dir);
        } catch (IOException e) {
            log.warn("process ['{}'] -> error while removing a temporary directory: " + instanceId, e.getMessage());
        }

        payload = payload
                .removeHeader(Payload.BASE_DIR)
                .removeHeader(Payload.WORKSPACE_DIR)
                .clearAttachments();

        return chain.process(payload);
    }

    private static boolean filter(Path p) {
        if (p.isAbsolute()) {
            log.warn("filter ['{}'] -> can't filter absolute paths", p);
            return true;
        }

        String n = p.toString();
        for (String i : InternalConstants.Files.IGNORED_FILES) {
            if (n.matches(i)) {
                return false;
            }
        }

        return true;
    }
}
