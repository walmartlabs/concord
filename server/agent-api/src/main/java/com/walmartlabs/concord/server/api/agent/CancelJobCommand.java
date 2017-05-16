package com.walmartlabs.concord.server.api.agent;

public class CancelJobCommand implements Command {

    private final String instanceId;

    public CancelJobCommand(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    @Override
    public String toString() {
        return "CancelJobCommand{" +
                "instanceId='" + instanceId + '\'' +
                '}';
    }
}
