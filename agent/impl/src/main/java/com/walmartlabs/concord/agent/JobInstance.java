package com.walmartlabs.concord.agent;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public interface JobInstance {

    Path getWorkDir();

    void kill();

    CompletableFuture<?> future();
}
