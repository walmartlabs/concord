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

import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import com.walmartlabs.concord.server.sdk.ProcessKey;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;

public class FormFilesStoringProcessor implements PayloadProcessor {

    private final ProcessLogManager logManager;

    @Inject
    public FormFilesStoringProcessor(ProcessLogManager logManager) {
        this.logManager = logManager;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Payload process(Chain chain, Payload payload) {
        ProcessKey processKey = payload.getProcessKey();

        Map<String, Object> cfg = payload.getHeader(Payload.CONFIGURATION);
        if (cfg == null) {
            return chain.process(payload);
        }

        Map<String, String> formFiles = (Map<String, String>) cfg.get(Constants.Files.FORM_FILES);
        if (formFiles == null) {
            return chain.process(payload);
        }

        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);

        try {
            for (Map.Entry<String, String> e : formFiles.entrySet()) {
                Path formFile = workspace.resolve(e.getKey());
                Path tmpFile = Paths.get(e.getValue());

                Path p = formFile.getParent();
                if (!Files.exists(p)) {
                    Files.createDirectories(p);
                }

                Files.move(tmpFile, formFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            logManager.error(processKey, "Error while saving form files", e);
            throw new ProcessException(processKey, "Error while saving form files", e);
        }

        return chain.process(payload);
    }
}
