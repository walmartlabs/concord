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
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.cfg.DependencyVersionConfiguration;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import com.walmartlabs.concord.server.sdk.ProcessKey;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * @deprecated replaced with the "dependencyVersions" policy.
 * @see com.walmartlabs.concord.policyengine.DependencyVersionsPolicy
 */
@Deprecated
public class DependencyVersionsExportProcessor implements PayloadProcessor {

    private final DependencyVersionConfiguration cfg;
    private final ProcessLogManager logManager;

    @Inject
    public DependencyVersionsExportProcessor(DependencyVersionConfiguration cfg, ProcessLogManager logManager) {
        this.cfg = cfg;
        this.logManager = logManager;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        ProcessKey processKey = payload.getProcessKey();
        if (cfg.getPath() == null) {
            return chain.process(payload);
        }

        logManager.info(processKey, "Storing default dependency versions...");

        Path ws = payload.getHeader(Payload.WORKSPACE_DIR);

        try {
            Path dst = Files.createDirectories(ws.resolve(Constants.Files.CONCORD_SYSTEM_DIR_NAME));
            IOUtils.copy(cfg.getPath(), dst.resolve(Constants.Files.DEPENDENCY_VERSIONS_FILE_NAME), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logManager.error(processKey, "Error while storing dependency versions: {}", e);
            throw new ProcessException(processKey, "Error while storing dependency versions: " + e.getMessage(), e);
        }

        return chain.process(payload);
    }
}
