package com.walmartlabs.concord.server.process;

import java.nio.file.Path;
import java.util.Map;

public class Payload implements AutoCloseable {

    private final String instanceId;
    private final String projectId;
    private final String initiator;
    private final String logFileName;
    private final Path data;

    public Payload(String instanceId, String projectId, String initiator, String logFileName, Path data) {
        this.instanceId = instanceId;
        this.projectId = projectId;
        this.initiator = initiator;
        this.logFileName = logFileName;
        this.data = data;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getInitiator() {
        return initiator;
    }

    public String getLogFileName() {
        return logFileName;
    }

    public Path getData() {
        return data;
    }

    @Override
    public void close() {
        data.toFile().delete();
    }
}
