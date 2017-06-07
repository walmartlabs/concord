package com.walmartlabs.concord.server.rpc;

import com.google.protobuf.Any;
import com.walmartlabs.concord.rpc.*;
import io.grpc.stub.StreamObserver;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Named
@Singleton
public class CommandQueueImpl extends TCommandQueueGrpc.TCommandQueueImplBase {

    // TODO expiration?
    private final Map<String, BlockingQueue<Command>> queues = new HashMap<>();

    public void add(String agentId, Command cmd) {
        BlockingQueue<Command> q;
        synchronized (queues) {
            q = queues.computeIfAbsent(agentId, k -> new LinkedBlockingQueue<>());
        }

        try {
            q.put(cmd);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void take(TCommandRequest request, StreamObserver<TCommandResponse> responseObserver) {
        String agentId = request.getAgentId();

        BlockingQueue<Command> q;
        synchronized (queues) {
            q = queues.computeIfAbsent(agentId, k -> new LinkedBlockingQueue<>());
        }

        Command cmd;
        try {
            cmd = q.take();
        } catch (InterruptedException e) {
            responseObserver.onError(e);
            return;
        }

        TCommandResponse resp = TCommandResponse.newBuilder()
                .setCommand(convert(cmd))
                .build();

        responseObserver.onNext(resp);

        responseObserver.onCompleted();
    }

    private static Any convert(Command cmd) {
        if (cmd instanceof CancelJobCommand) {
            CancelJobCommand c = (CancelJobCommand) cmd;
            return Any.pack(TCancelJobCommand.newBuilder()
                    .setInstanceId(c.getInstanceId())
                    .build());
        } else {
            throw new IllegalArgumentException("Unsupported command type: " + cmd.getClass());
        }
    }
}
