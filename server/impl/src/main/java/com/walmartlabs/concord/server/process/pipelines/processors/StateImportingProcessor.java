package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.project.Constants;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

@Named
public class StateImportingProcessor implements PayloadProcessor {

    private static final Logger log = LoggerFactory.getLogger(StateImportingProcessor.class);

    private final ProcessStateManager stateManager;

    @Inject
    public StateImportingProcessor(ProcessStateManager stateManager) {
        this.stateManager = stateManager;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        UUID instanceId = payload.getInstanceId();
        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);

        stateManager.transaction(tx -> {
            stateManager.delete(tx, instanceId);
            stateManager.importPath(tx, instanceId, workspace, StateImportingProcessor::filter);
        });

        Path dir = payload.getHeader(Payload.BASE_DIR, workspace);
        try {
            IOUtils.deleteRecursively(dir);
        } catch (IOException e) {
            log.warn("process ['{}'] -> error while removing a temporary directory: " + instanceId, e.getMessage());
        }

        payload = payload
                .removeHeader(Payload.BASE_DIR)
                .removeHeader(Payload.WORKSPACE_DIR)
                .clearAttachments();

        return chain.process(payload);
    }

    private static boolean filter(Path p) {
        if (p.isAbsolute()) {
            log.warn("filter ['{}'] -> can't filter absolute paths", p);
            return true;
        }

        String n = p.toString();
        for (String i : Constants.Files.IGNORED_FILES) {
            if (n.matches(i)) {
                return false;
            }
        }

        return true;
    }
}
