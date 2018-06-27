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
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.state.ProcessMetadataManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

@Named
public class FlowMetadataProcessor implements PayloadProcessor {

    private static final Logger log = LoggerFactory.getLogger(FlowMetadataProcessor.class);

    private final ProcessMetadataManager metadataManager;

    @Inject
    public FlowMetadataProcessor(ProcessMetadataManager metadataManager) {
        this.metadataManager = metadataManager;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        UUID instanceId = payload.getInstanceId();
        metadataManager.deleteOnFailureMarker(instanceId);
        metadataManager.deleteOnCancelMarker(instanceId);

        ProjectDefinition pd = payload.getHeader(Payload.PROJECT_DEFINITION);
        if (pd == null) {
            // TODO reload Payload.PROJECT_DEFINITION for forked instances
            return chain.process(payload);
        }

        Collection<String> profiles = payload.getHeader(Payload.ACTIVE_PROFILES);

        if (hasFlow(pd, profiles, InternalConstants.Flows.ON_FAILURE_FLOW)) {
            boolean suppressed = getBoolean(payload, InternalConstants.Request.DISABLE_ON_FAILURE_KEY);
            if (suppressed) {
                log.info("process ['{}'] -> onFailure is suppressed, skipping...", instanceId);
            } else {
                metadataManager.addOnFailureMarker(instanceId);
                log.info("process ['{}'] -> added onFailure marker", instanceId);
            }
        }

        if (hasFlow(pd, profiles, InternalConstants.Flows.ON_CANCEL_FLOW)) {
            boolean suppressed = getBoolean(payload, InternalConstants.Request.DISABLE_ON_CANCEL_KEY);
            if (suppressed) {
                log.info("process ['{}'] -> onCancel is suppressed, skipping...", instanceId);
            } else {
                metadataManager.addOnCancelMarker(instanceId);
                log.info("process ['{}'] -> added onCancel marker", instanceId);
            }
        }

        return chain.process(payload);
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
