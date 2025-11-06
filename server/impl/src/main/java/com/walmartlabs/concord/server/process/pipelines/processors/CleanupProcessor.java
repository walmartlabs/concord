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

import com.walmartlabs.concord.common.PathUtils;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import org.slf4j.MDC;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class CleanupProcessor implements FinalizerProcessor {

    private final ProcessLogManager logManager;

    @Inject
    public CleanupProcessor(ProcessLogManager logManager) {
        this.logManager = logManager;
    }

    @Override
    public void process(Payload payload) {
        // cleanup the MDC
        MDC.clear();

        delete(payload.getProcessKey(), payload.getHeader(Payload.WORKSPACE_DIR));
        delete(payload.getProcessKey(), payload.getHeader(Payload.BASE_DIR));
    }

    private void delete(ProcessKey processKey, Path p) {
        if (p == null || !Files.exists(p)) {
            return;
        }

        try {
            PathUtils.deleteRecursively(p);
        } catch (IOException e) {
            logManager.warn(processKey, "Unable to delete the working directory: {}", p, e);
        }
    }
}
