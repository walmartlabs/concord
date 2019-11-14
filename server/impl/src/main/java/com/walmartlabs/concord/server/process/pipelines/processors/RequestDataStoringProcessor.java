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
import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.ProcessKey;
import com.walmartlabs.concord.server.process.logs.LogManager;

import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Stores payload's request data as a JSON file.
 */
public class RequestDataStoringProcessor implements PayloadProcessor {

    private final LogManager logManager;

    @Inject
    public RequestDataStoringProcessor(LogManager logManager) {
        this.logManager = logManager;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        ProcessKey processKey = payload.getProcessKey();

        Map<String, Object> cfg = payload.getHeader(Payload.CONFIGURATION);
        if (cfg == null) {
            return chain.process(payload);
        }

        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);
        Path dst = workspace.resolve(InternalConstants.Files.REQUEST_DATA_FILE_NAME);

        try (OutputStream out = Files.newOutputStream(dst)) {
            ObjectMapper om = new ObjectMapper();
            om.writeValue(out, cfg);
        } catch (IOException e) {
            logManager.error(processKey, "Error while saving a metadata file: " + dst, e);
            throw new ProcessException(processKey, "Error while saving a metadata file: " + dst, e);
        }

        return chain.process(payload);
    }
}
