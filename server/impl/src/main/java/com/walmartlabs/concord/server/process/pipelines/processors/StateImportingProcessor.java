package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.state.ProcessStateManagerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Path;

@Named
public class StateImportingProcessor implements PayloadProcessor {

    private static final Logger log = LoggerFactory.getLogger(StateImportingProcessor.class);

    private final ProcessStateManagerImpl stateManager;

    @Inject
    public StateImportingProcessor(ProcessStateManagerImpl stateManager) {
        this.stateManager = stateManager;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);
        stateManager.importPath(payload.getInstanceId(), null, workspace);

        Path dir = payload.getHeader(Payload.BASE_DIR, workspace);
        try {
            IOUtils.deleteRecursively(dir);
        } catch (IOException e) {
            log.warn("process ['{}'] -> error while removing a temporary directory: " + payload.getInstanceId(), e.getMessage());
        }

        payload = payload
                .removeHeader(Payload.BASE_DIR)
                .removeHeader(Payload.WORKSPACE_DIR)
                .clearAttachments();

        return chain.process(payload);
    }
}
