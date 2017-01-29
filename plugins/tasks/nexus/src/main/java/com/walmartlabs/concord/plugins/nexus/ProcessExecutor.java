package com.walmartlabs.concord.plugins.nexus;

public interface ProcessExecutor {

    void execute(String processKey, String userId, String path, String version);
}
