package com.walmartlabs.concord.agent;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public interface JobInstance {

    Path logFile();

    void cancel();

    CompletableFuture<?> future();
}
