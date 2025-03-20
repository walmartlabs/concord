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
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;

import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

public class ResumeConfigurationProcessor implements PayloadProcessor {

    @Override
    public Payload process(Chain chain, Payload payload) {
        // configuration from the user's request
        Map<String, Object> cfg = payload.getHeader(Payload.CONFIGURATION, Collections.emptyMap());

        // _main.json file in the workspace
        Map<String, Object> workspaceCfg = getWorkspaceCfg(payload);

        // we'll use the arguments only from the request
        workspaceCfg.remove(Constants.Request.ARGUMENTS_KEY);

        // TODO automatically provided variables

        // TODO overrides from policies

        // create the resulting configuration
        Map<String, Object> m = ConfigurationUtils.deepMerge(workspaceCfg, cfg);
        payload = payload.putHeader(Payload.CONFIGURATION, m);

        return chain.process(payload);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getWorkspaceCfg(Payload payload) {
        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);
        Path src = workspace.resolve(Constants.Files.CONFIGURATION_FILE_NAME);
        if (!Files.exists(src)) {
            return Collections.emptyMap();
        }

        try (InputStream in = Files.newInputStream(src)) {
            ObjectMapper om = new ObjectMapper();
            return om.readValue(in, Map.class);
        } catch (IOException e) {
            throw new ProcessException(payload.getProcessKey(), "Invalid request data format", e, Status.BAD_REQUEST);
        }
    }
}
