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

import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.logs.LogManager;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

@Named
public class DependenciesProcessor implements PayloadProcessor {

    private static final Logger log = LoggerFactory.getLogger(DependenciesProcessor.class);

    private final LogManager logManager;

    @Inject
    public DependenciesProcessor(LogManager logManager) {
        this.logManager = logManager;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Payload process(Chain chain, Payload payload) {
        UUID instanceId = payload.getInstanceId();
        Map<String, Object> req = payload.getHeader(Payload.REQUEST_DATA_MAP);

        // get a list of dependencies from the req data
        Collection<String> deps = deps(instanceId, req);
        if (deps == null) {
            return chain.process(payload);
        }

        boolean failed = false;
        for (String d : deps) {
            try {
                new URI(d);
            } catch (URISyntaxException e) {
                logManager.error(instanceId, "Invalid dependency URL: " + d);
                failed = true;
            }
        }

        if (failed) {
            throw new ProcessException(instanceId, "Invalid dependency list");
        }

        req.put(InternalConstants.Request.DEPENDENCIES_KEY, deps);
        payload = payload.putHeader(Payload.REQUEST_DATA_MAP, req);

        log.info("process ['{}'] -> done", instanceId);
        return chain.process(payload);
    }

    @SuppressWarnings("unchecked")
    private Collection<String> deps(UUID instanceId, Map<String, Object> req) {
        Object o = req.get(InternalConstants.Request.DEPENDENCIES_KEY);
        if (o == null) {
            return null;
        }

        if (o instanceof Collection) {
            return (Collection<String>) o;
        }

        if (o instanceof ScriptObjectMirror) {
            ScriptObjectMirror m = (ScriptObjectMirror) o;
            if (!m.isArray()) {
                logManager.error(instanceId, "Invalid dependencies object type. Expected a JavaScript array, got: " + m);
                throw new ProcessException(instanceId, "Invalid dependencies object type. Expected a JavaScript array, got: " + m);
            }

            String[] as = m.to(String[].class);
            return Arrays.asList(as);
        }

        logManager.error(instanceId, "Invalid dependencies object type. Expected an array or a collection, got: " + o.getClass());
        throw new ProcessException(instanceId, "Invalid dependencies object type. Expected an array or a collection, got: " + o.getClass());
    }
}
