package com.walmartlabs.concord.agent;

import java.nio.file.Path;

public interface JobExecutor {

    void exec(String id, Path workDir, String entryPoint) throws ExecutionException;
}
