package com.walmartlabs.concord.rpc;

import com.walmartlabs.concord.rpc.TProcessHeartbeatServiceGrpc.TProcessHeartbeatServiceBlockingStub;
import com.walmartlabs.concord.sdk.ClientException;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;

import java.util.concurrent.TimeUnit;

public class ProcessHeartbeatServiceImpl implements ProcessHeartbeatService {

    private static final long PING_TIMEOUT = 5000;

    private final ManagedChannel channel;

    public ProcessHeartbeatServiceImpl(ManagedChannel channel) {
        this.channel = channel;
    }

    @Override
    public void ping(String instanceId) throws ClientException {
        TProcessHeartbeatServiceBlockingStub stub = TProcessHeartbeatServiceGrpc.newBlockingStub(channel);

        try {
            stub.withDeadlineAfter(PING_TIMEOUT, TimeUnit.MILLISECONDS)
                    .heartbeat(TProcessHeartbeatRequest.newBuilder()
                            .setInstanceId(instanceId)
                            .build());
        } catch (StatusRuntimeException e) {
            throw new ClientException(e.getMessage(), e);
        }
    }
}
