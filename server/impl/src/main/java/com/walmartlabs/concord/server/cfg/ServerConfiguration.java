package com.walmartlabs.concord.server.cfg;

import java.io.Serializable;
import java.net.URI;
import java.nio.file.Path;

public class ServerConfiguration implements Serializable {

    private final int port;
    private final URI exposedAddress;
    private final String agentImageName;
    private final Path workDir;

    public ServerConfiguration(int port, URI exposedAddress, String agentImageName, Path workDir) {
        this.port = port;
        this.exposedAddress = exposedAddress;
        this.agentImageName = agentImageName;
        this.workDir = workDir;
    }

    public int getPort() {
        return port;
    }

    public URI getExposedAddress() {
        return exposedAddress;
    }

    public String getAgentImageName() {
        return agentImageName;
    }

    public Path getWorkDir() {
        return workDir;
    }
}
