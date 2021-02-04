package com.walmartlabs.concord.server.process.pipelines.processors;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2021 Walmart Inc.
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
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public class ForkConfigurationProcessor implements PayloadProcessor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Payload process(Chain chain, Payload payload) {
        // configuration from the user's request
        Map<String, Object> cfg = payload.getHeader(Payload.CONFIGURATION, Collections.emptyMap());

        // _main.json file in the workspace
        Map<String, Object> workspaceCfg = getWorkspaceCfg(payload);

        // create the resulting configuration
        Map<String, Object> m = ConfigurationUtils.deepMerge(workspaceCfg, cfg, createForkCfg(payload));
        payload = payload.putHeader(Payload.CONFIGURATION, m);

        return chain.process(payload);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getWorkspaceCfg(Payload payload) {
        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);
        Path src = workspace.resolve(Constants.Files.REQUEST_DATA_FILE_NAME);
        if (!Files.exists(src)) {
            return Collections.emptyMap();
        }

        try (InputStream in = Files.newInputStream(src)) {
            return objectMapper.readValue(in, Map.class);
        } catch (IOException e) {
            throw new ProcessException(payload.getProcessKey(), "Invalid request data format", e, Response.Status.BAD_REQUEST);
        }
    }

    private static Map<String, Object> createForkCfg(Payload payload) {
        UUID id = payload.getHeader(Payload.PARENT_INSTANCE_ID);
        if (id == null) {
            return Collections.emptyMap();
        }

        return Collections.singletonMap(Constants.Request.ARGUMENTS_KEY,
                Collections.singletonMap(Constants.Request.PARENT_INSTANCE_ID_KEY, id.toString()));
    }
}
