package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.server.cfg.RunnerConfiguration;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.keys.HeaderKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Adds necessary runtime dependencies to a payload.
 */
@Named
public class RunnerProcessor implements PayloadProcessor {

    private static final Logger log = LoggerFactory.getLogger(RunnerProcessor.class);
    public static final HeaderKey<String> ENTRY_POINT_NAME = HeaderKey.register("_entryPointName", String.class);

    private final RunnerConfiguration runnerCfg;

    @Inject
    public RunnerProcessor(RunnerConfiguration runnerCfg) {
        this.runnerCfg = runnerCfg;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);

        Path src = runnerCfg.getPath();
        Path dst = workspace.resolve(runnerCfg.getTargetName());

        try {
            Files.copy(src, dst);
        } catch (IOException e) {
            log.error("process ['{}'] -> error while copying dependencies", payload.getInstanceId(), e);
            throw new ProcessException("Error while copying dependencies", e);
        }

        payload = payload.putHeader(ENTRY_POINT_NAME, runnerCfg.getTargetName());

        return chain.process(payload);
    }
}
