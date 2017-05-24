package com.walmartlabs.concord.rpc;

import java.nio.file.Path;

public interface JobQueue {

    JobEntry take() throws ClientException;

    void update(String instanceId, JobStatus status) throws ClientException;

    void appendLog(String instanceId, byte[] data) throws ClientException;

    void uploadAttachments(String instanceId, Path src) throws ClientException;
}
