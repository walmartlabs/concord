package com.walmartlabs.concord.agent;

import java.io.Serializable;
import java.nio.file.Path;

public class Configuration implements Serializable {

    private final Path logDir;
    private final Path payloadDir;
    private final String agentJavaCmd;

    public Configuration(Path logDir, Path payloadDir, String agentJavaCmd) {
        this.logDir = logDir;
        this.payloadDir = payloadDir;
        this.agentJavaCmd = agentJavaCmd;
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
}
