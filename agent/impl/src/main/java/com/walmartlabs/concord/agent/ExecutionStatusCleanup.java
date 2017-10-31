package com.walmartlabs.concord.agent;

import java.util.concurrent.TimeUnit;

public class ExecutionStatusCleanup implements Runnable {

    private static final long CLEANUP_INTERVAL = TimeUnit.SECONDS.toMillis(30);

    private final ExecutionManager executionManager;

    public ExecutionStatusCleanup(ExecutionManager executionManager) {
        this.executionManager = executionManager;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            executionManager.cleanup();

            try {
                Thread.sleep(CLEANUP_INTERVAL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
