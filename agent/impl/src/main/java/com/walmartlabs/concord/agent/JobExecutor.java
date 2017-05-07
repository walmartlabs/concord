package com.walmartlabs.concord.agent;

import java.nio.file.Path;

public interface JobExecutor {

    JobInstance start(String instanceId, Path workDir, String entryPoint) throws ExecutionException;
}
