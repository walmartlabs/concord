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

import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.project.model.ProjectDefinition;
import com.walmartlabs.concord.project.model.ProjectDefinitionUtils;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Named
public class ProcessHandlersProcessor implements PayloadProcessor {

    private static final Logger log = LoggerFactory.getLogger(ProcessHandlersProcessor.class);

    @Override
    public Payload process(Chain chain, Payload payload) {
        Set<String> handlers = new HashSet<>();

        ProjectDefinition pd = payload.getHeader(Payload.PROJECT_DEFINITION);
        if (pd == null) {
            // TODO reload Payload.PROJECT_DEFINITION for forked instances
            return chain.process(payload);
        }

        Collection<String> profiles = payload.getHeader(Payload.ACTIVE_PROFILES);

        update(payload, pd, profiles, handlers, Constants.Flows.ON_FAILURE_FLOW, InternalConstants.Request.DISABLE_ON_FAILURE_KEY);
        update(payload, pd, profiles, handlers, Constants.Flows.ON_CANCEL_FLOW, InternalConstants.Request.DISABLE_ON_CANCEL_KEY);
        update(payload, pd, profiles, handlers, Constants.Flows.ON_TIMEOUT_FLOW, InternalConstants.Request.DISABLE_ON_TIMEOUT_KEY);

        payload = payload.putHeader(Payload.PROCESS_HANDLERS, handlers);

        return chain.process(payload);
    }

    private static void update(Payload payload, ProjectDefinition pd, Collection<String> profiles, Set<String> handlers, String flow, String disableFlag) {
        ProcessKey processKey = payload.getProcessKey();
        if (hasFlow(pd, profiles, flow)) {
            boolean suppressed = getBoolean(payload, disableFlag);
            if (suppressed) {
                log.info("process ['{}'] -> {} is suppressed, skipping...", processKey, flow);
            } else {
                handlers.add(flow);
                log.info("process ['{}'] -> added {} handler", processKey, flow);
            }
        }
    }

    private static boolean hasFlow(ProjectDefinition pd, Collection<String> profiles, String key) {
        return ProjectDefinitionUtils.getFlow(pd, profiles, key) != null;
    }

    @SuppressWarnings("unchecked")
    private static boolean getBoolean(Payload payload, String key) {
        Map<String, Object> req = payload.getHeader(Payload.REQUEST_DATA_MAP);
        if (req == null) {
            return false;
        }

        Object v = req.get(key);
        if (v == null) {
            return false;
        }

        return (Boolean) v;
    }
}
