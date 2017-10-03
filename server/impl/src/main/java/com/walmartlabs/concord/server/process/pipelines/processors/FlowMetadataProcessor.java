package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.project.model.ProjectDefinition;
import com.walmartlabs.concord.project.model.ProjectDefinitionUtils;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.metrics.WithTimer;
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
    @WithTimer
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

        if (hasFlow(pd, profiles, Constants.Flows.ON_FAILURE_FLOW)) {
            boolean suppressed = getBoolean(payload, Constants.Request.DISABLE_ON_FAILURE_KEY);
            if (suppressed) {
                log.info("process ['{}'] -> onFailure is suppressed, skipping...", instanceId);
            } else {
                metadataManager.addOnFailureMarker(instanceId);
                log.info("process ['{}'] -> added onFailure marker", instanceId);
            }
        }

        if (hasFlow(pd, profiles, Constants.Flows.ON_CANCEL_FLOW)) {
            boolean suppressed = getBoolean(payload, Constants.Request.DISABLE_ON_CANCEL_KEY);
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
