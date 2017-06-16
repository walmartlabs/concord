package com.walmartlabs.concord.rpc;

public class CancelJobCommand implements Command {

    private final String instanceId;

    public CancelJobCommand(String instanceId) {
        this.instanceId = instanceId;
    }

    @Override
    public CommandType getType() {
        return CommandType.CANCEL_JOB;
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
