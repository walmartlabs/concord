package com.walmartlabs.concord.agent;

import javax.inject.Named;
import javax.inject.Provider;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Named
public class ExecutionPoolProvider implements Provider<ExecutorService> {

    @Override
    public ExecutorService get() {
        // TODO configuration
        return Executors.newCachedThreadPool();
    }
}
