package com.walmartlabs.concord.agent;

import java.io.Serializable;
import java.net.URI;
import java.nio.file.Path;

public class Configuration implements Serializable {

    private final String serverHost;
    private final int serverPort;
    private final Path logDir;
    private final Path payloadDir;
    private final String agentJavaCmd;
    private final Path dependencyCacheDir;
    private final Path runnerPath;

    public Configuration(String serverHost, int serverPort,
                         Path logDir, Path payloadDir, String agentJavaCmd,
                         Path dependencyCacheDir, Path runnerPath) {

        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.logDir = logDir;
        this.payloadDir = payloadDir;
        this.agentJavaCmd = agentJavaCmd;
        this.dependencyCacheDir = dependencyCacheDir;
        this.runnerPath = runnerPath;
    }

    public String getServerHost() {
        return serverHost;
    }

    public int getServerPort() {
        return serverPort;
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
