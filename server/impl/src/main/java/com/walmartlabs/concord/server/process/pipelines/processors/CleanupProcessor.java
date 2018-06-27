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


import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.logs.LogManager;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Named
public class CleanupProcessor implements FinalizerProcessor {

    private final LogManager logManager;

    @Inject
    public CleanupProcessor(LogManager logManager) {
        this.logManager = logManager;
    }

    @Override
    public void process(Payload payload) {
        delete(payload.getInstanceId(), payload.getHeader(Payload.WORKSPACE_DIR));
        delete(payload.getInstanceId(), payload.getHeader(Payload.BASE_DIR));
    }

    private void delete(UUID instanceId, Path p) {
        if (p == null || !Files.exists(p)) {
            return;
        }

        try {
            IOUtils.deleteRecursively(p);
        } catch (IOException e) {
            logManager.warn(instanceId, "Unable to delete the working directory: {}", p, e);
        }
    }
}
