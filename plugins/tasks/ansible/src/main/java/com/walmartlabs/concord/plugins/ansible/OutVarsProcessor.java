package com.walmartlabs.concord.plugins.ansible;

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
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.sdk.Context;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class OutVarsProcessor {

    private Path outVarsFile;

    public void prepare(Map<String, Object> args, Map<String, String> env, Path workDir, Path tmpDir) throws IOException {
        String outVars = ArgUtils.getListAsString(args, AnsibleConstants.OUT_VARS_KEY);
        if (outVars == null) {
            return;
        }
        env.put("CONCORD_OUT_VARS", outVars);
        outVarsFile = tmpDir.resolve("out_vars.json");
        env.put("CONCORD_OUT_VARS_FILE", workDir.relativize(outVarsFile).toString());
    }

    @SuppressWarnings("unchecked")
    public void process(Context context) throws IOException {
        if (outVarsFile == null || !Files.exists(outVarsFile)) {
            return;
        }

        ObjectMapper om = new ObjectMapper();
        Map<String, Object> m = new HashMap<>();
        try (InputStream in = Files.newInputStream(outVarsFile)) {
            m.putAll(om.readValue(in, Map.class));
        }
        m.forEach(context::setVariable);
    }

    public void postProcess() throws IOException {
        if (outVarsFile != null) {
            Files.delete(outVarsFile);
        }
    }
}
