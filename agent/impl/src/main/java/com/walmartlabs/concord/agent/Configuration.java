package com.walmartlabs.concord.agent;

import java.io.Serializable;
import java.nio.file.Path;

public class Configuration implements Serializable {

    private final Path logDir;
    private final Path payloadDir;
    private final String agentJavaCmd;
    private final Path dependencyCacheDir;

    public Configuration(Path logDir, Path payloadDir, String agentJavaCmd, Path dependencyCacheDir) {
        this.logDir = logDir;
        this.payloadDir = payloadDir;
        this.agentJavaCmd = agentJavaCmd;
        this.dependencyCacheDir = dependencyCacheDir;
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
}
