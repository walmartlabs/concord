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
        ProjectDefinition pd = payload.getHeader(Payload.PROJECT_DEFINITION);
        if (pd == null) {
            return chain.process(payload);
        }

        Collection<String> profiles = payload.getHeader(Payload.ACTIVE_PROFILES);

        if (hasFlow(pd, profiles, Constants.Flows.ON_FAILURE_FLOW)) {
            metadataManager.addOnFailureMarker(payload.getInstanceId());
            log.info("process ['{}'] -> added onFailure marker", payload.getInstanceId());
        }

        if (hasFlow(pd, profiles, Constants.Flows.ON_CANCEL_FLOW)) {
            metadataManager.addOnCancelMarker(payload.getInstanceId());
            log.info("process ['{}'] -> added onCancel marker", payload.getInstanceId());
        }

        return chain.process(payload);
    }

    private static boolean hasFlow(ProjectDefinition pd, Collection<String> profiles, String key) {
        return ProjectDefinitionUtils.getFlow(pd, profiles, key) != null;
    }
}
