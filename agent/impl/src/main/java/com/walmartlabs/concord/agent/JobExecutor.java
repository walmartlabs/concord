package com.walmartlabs.concord.agent;

import java.nio.file.Path;
import java.util.Collection;

public interface JobExecutor {

    void exec(String id, Path workDir, String entryPoint, Collection<String> jvmArgs) throws ExecutionException;
}
