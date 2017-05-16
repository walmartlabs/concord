package com.walmartlabs.concord.server.api.agent;

import java.nio.file.Path;

public class JobEntry {

    private final String instanceId;
    private final JobType jobType;
    private final Path payload;

    public JobEntry(String instanceId, JobType jobType, Path payload) {
        this.instanceId = instanceId;
        this.jobType = jobType;
        this.payload = payload;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public JobType getJobType() {
        return jobType;
    }

    public Path getPayload() {
        return payload;
    }

    @Override
    public String toString() {
        return "JobEntry{" +
                "instanceId='" + instanceId + '\'' +
                ", jobType=" + jobType +
                ", payload=" + payload +
                '}';
    }
}
