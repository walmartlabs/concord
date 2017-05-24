package com.walmartlabs.concord.rpc;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;

import java.util.concurrent.TimeUnit;

public class CommandQueueImpl implements CommandQueue {

    private static final int POLL_TIMEOUT = 5000;
    private static final int RETRY_DELAY = 1000;

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
        TCommandQueueGrpc.TCommandQueueBlockingStub blockingStub = TCommandQueueGrpc.newBlockingStub(channel);
        while (true) {
            TCommandResponse resp;
            try {
                TCommandRequest req = TCommandRequest.newBuilder()
                        .setAgentId(agentId)
                        .build();

                resp = blockingStub.withDeadlineAfter(POLL_TIMEOUT, TimeUnit.MILLISECONDS)
                        .poll(req);
            } catch (StatusRuntimeException e) {
                throw new ClientException(e.getMessage(), e);
            }

            if (resp.hasCommand()) {
                return convert(resp.getCommand());
            }

            try {
                Thread.sleep(RETRY_DELAY);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
