package com.walmartlabs.concord.plugins.ansible;

import java.util.Map;

public class TaskResult {

    private final boolean success;

    private final Map<String, Object> result;

    private final int exitCode;

    public TaskResult(boolean success, Map<String, Object> result, int exitCode) {
        this.success = success;
        this.result = result;
        this.exitCode = exitCode;
    }

    public boolean isSuccess() {
        return success;
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public int getExitCode() {
        return exitCode;
    }
}
