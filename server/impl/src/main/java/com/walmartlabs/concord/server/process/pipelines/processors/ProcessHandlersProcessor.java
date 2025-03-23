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

import com.walmartlabs.concord.process.loader.model.ProcessDefinition;
import com.walmartlabs.concord.process.loader.model.ProcessDefinitionUtils;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.process.Payload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Determines whether the process has onFailure/onCancel/onTimeout/etc handlers
 * configured. If there is a relevant flow defined and it is not disabled in
 * the process arguments, it will be added to the list of the process' handlers.
 */
public class ProcessHandlersProcessor implements PayloadProcessor {

    private static final Logger log = LoggerFactory.getLogger(ProcessHandlersProcessor.class);

    @Override
    public Payload process(Chain chain, Payload payload) {
        Set<String> handlers = new HashSet<>();

        ProcessDefinition pd = payload.getHeader(Payload.PROJECT_DEFINITION);
        if (pd == null) {
            return chain.process(payload);
        }

        Collection<String> profiles = payload.getHeader(Payload.ACTIVE_PROFILES);

        update(payload, pd, profiles, handlers, Constants.Flows.ON_FAILURE_FLOW, Constants.Request.DISABLE_ON_FAILURE_KEY);
        update(payload, pd, profiles, handlers, Constants.Flows.ON_CANCEL_FLOW, Constants.Request.DISABLE_ON_CANCEL_KEY);
        update(payload, pd, profiles, handlers, Constants.Flows.ON_TIMEOUT_FLOW, Constants.Request.DISABLE_ON_TIMEOUT_KEY);

        payload = payload.putHeader(Payload.PROCESS_HANDLERS, handlers);

        return chain.process(payload);
    }

    private static void update(Payload payload, ProcessDefinition pd, Collection<String> profiles, Set<String> handlers, String flow, String disableFlag) {
        if (hasFlow(pd, profiles, flow)) {
            boolean suppressed = getBoolean(payload, disableFlag);
            if (suppressed) {
                log.debug("process -> {} is suppressed, skipping...", flow);
            } else {
                handlers.add(flow);
                log.debug("process -> added {} handler", flow);
            }
        }
    }

    private static boolean hasFlow(ProcessDefinition pd, Collection<String> profiles, String key) {
        return ProcessDefinitionUtils.getFlow(pd, profiles, key) != null;
    }

    private static boolean getBoolean(Payload payload, String key) {
        Map<String, Object> cfg = payload.getHeader(Payload.CONFIGURATION);
        if (cfg == null) {
            return false;
        }

        Object v = cfg.get(key);
        if (v == null) {
            return false;
        }

        return (Boolean) v;
    }
}
