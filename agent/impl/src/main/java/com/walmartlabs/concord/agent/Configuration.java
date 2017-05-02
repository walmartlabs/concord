package com.walmartlabs.concord.agent;

import java.io.Serializable;
import java.nio.file.Path;

public class Configuration implements Serializable {

    private final Path logDir;
    private final Path payloadDir;
    private final String agentJavaCmd;
    private final Path dependencyCacheDir;
    private final Path runnerPath;

    public Configuration(Path logDir, Path payloadDir, String agentJavaCmd, Path dependencyCacheDir, Path runnerPath) {
        this.logDir = logDir;
        this.payloadDir = payloadDir;
        this.agentJavaCmd = agentJavaCmd;
        this.dependencyCacheDir = dependencyCacheDir;
        this.runnerPath = runnerPath;
    }

    public Path getLogDir() {
        return logDir;
    }

    public Path getPayloadDir() {
        return payloadDir;
    }

    public String getAgentJavaCmd() {
        return agentJavaCmd;
    }

    public Path getDependencyCacheDir() {
        return dependencyCacheDir;
    }

    public Path getRunnerPath() {
        return runnerPath;
    }
}
