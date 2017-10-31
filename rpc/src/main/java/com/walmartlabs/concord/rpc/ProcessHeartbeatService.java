package com.walmartlabs.concord.rpc;

import com.walmartlabs.concord.sdk.ClientException;

public interface ProcessHeartbeatService {

    void ping(String instanceId) throws ClientException;
}
