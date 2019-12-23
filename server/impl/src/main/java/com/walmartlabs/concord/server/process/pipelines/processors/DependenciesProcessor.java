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
import com.walmartlabs.concord.server.process.ProcessKey;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import jdk.nashorn.api.scripting.ScriptObjectMirror;

import javax.inject.Inject;
import javax.inject.Named;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

@Named
public class DependenciesProcessor implements PayloadProcessor {

    private final ProcessLogManager logManager;

    @Inject
    public DependenciesProcessor(ProcessLogManager logManager) {
        this.logManager = logManager;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        ProcessKey processKey = payload.getProcessKey();
        Map<String, Object> cfg = payload.getHeader(Payload.CONFIGURATION);

        // get a list of dependencies from the cfg data
        Collection<String> deps = deps(processKey, cfg);
        if (deps == null) {
            return chain.process(payload);
        }

        boolean failed = false;
        for (String d : deps) {
            try {
                new URI(d);
            } catch (URISyntaxException e) {
                logManager.error(processKey, "Invalid dependency URL: " + d);
                failed = true;
            }
        }

        if (failed) {
            throw new ProcessException(processKey, "Invalid dependency list");
        }

        cfg.put(Constants.Request.DEPENDENCIES_KEY, deps);
        payload = payload.putHeader(Payload.CONFIGURATION, cfg);

        return chain.process(payload);
    }

    @SuppressWarnings("unchecked")
    private Collection<String> deps(ProcessKey processKey, Map<String, Object> req) {
        Object o = req.get(Constants.Request.DEPENDENCIES_KEY);
        if (o == null) {
            return null;
        }

        if (o instanceof Collection) {
            return (Collection<String>) o;
        }

        if (o instanceof ScriptObjectMirror) {
            ScriptObjectMirror m = (ScriptObjectMirror) o;
            if (!m.isArray()) {
                logManager.error(processKey, "Invalid dependencies object type. Expected a JavaScript array, got: " + m);
                throw new ProcessException(processKey, "Invalid dependencies object type. Expected a JavaScript array, got: " + m);
            }

            String[] as = m.to(String[].class);
            return Arrays.asList(as);
        }

        logManager.error(processKey, "Invalid dependencies object type. Expected an array or a collection, got: " + o.getClass());
        throw new ProcessException(processKey, "Invalid dependencies object type. Expected an array or a collection, got: " + o.getClass());
    }
}
