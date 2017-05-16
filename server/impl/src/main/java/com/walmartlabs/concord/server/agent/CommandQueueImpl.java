package com.walmartlabs.concord.server.agent;

import com.google.protobuf.Any;
import com.walmartlabs.concord.server.api.agent.*;
import io.grpc.stub.StreamObserver;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

@Named
@Singleton
public class CommandQueueImpl extends TCommandQueueGrpc.TCommandQueueImplBase {

    // TODO expiration?
    private final Map<String, BlockingQueue<Command>> queues = new HashMap<>();

    public void add(String agentId, Command cmd) {
        BlockingQueue<Command> q;
        synchronized (queues) {
            q = queues.get(agentId);
            if (q == null) {
                q = new ArrayBlockingQueue<>(16);
                queues.put(agentId, q);
            }
        }

        try {
            q.put(cmd);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void poll(TCommandRequest request, StreamObserver<TCommandResponse> responseObserver) {
        String agentId = request.getAgentId();

        TCommandResponse.Builder b = TCommandResponse.newBuilder();

        BlockingQueue<Command> q;
        synchronized (queues) {
            q = queues.get(agentId);
        }

        if (q != null) {
            Command cmd = q.poll();
            if (cmd != null) {
                b.setCommand(convert(cmd));
            }
        }

        responseObserver.onNext(b.build());
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
