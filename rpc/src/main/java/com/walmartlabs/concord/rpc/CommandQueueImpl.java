package com.walmartlabs.concord.rpc;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.walmartlabs.concord.rpc.TCommandQueueGrpc.TCommandQueueBlockingStub;
import io.grpc.ManagedChannel;

public class CommandQueueImpl implements CommandQueue {

    private final String agentId;
    private final ManagedChannel channel;

    public CommandQueueImpl(String agentId, ManagedChannel channel) {
        this.agentId = agentId;
        this.channel = channel;
    }

    private static Command convert(Any any) throws ClientException {
        if (any.is(TCancelJobCommand.class)) {
            TCancelJobCommand cmd;
            try {
                cmd = any.unpack(TCancelJobCommand.class);
            } catch (InvalidProtocolBufferException e) {
                throw new ClientException(e.getMessage(), e);
            }
            return new CancelJobCommand(cmd.getInstanceId());
        } else {
            throw new IllegalArgumentException("Unsupported command type: " + any.getTypeUrl());
        }
    }

    @Override
    public Command take() throws ClientException {
        TCommandQueueBlockingStub stub = TCommandQueueGrpc.newBlockingStub(channel);

        TCommandRequest req = TCommandRequest.newBuilder()
                .setAgentId(agentId)
                .build();

        TCommandResponse resp = stub.take(req);
        return convert(resp.getCommand());
    }
}
