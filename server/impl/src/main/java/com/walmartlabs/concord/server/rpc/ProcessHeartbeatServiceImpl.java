package com.walmartlabs.concord.server.rpc;

import com.google.protobuf.Empty;
import com.walmartlabs.concord.rpc.TProcessHeartbeatRequest;
import com.walmartlabs.concord.rpc.TProcessHeartbeatServiceGrpc;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import io.grpc.stub.StreamObserver;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.UUID;

@Named
public class ProcessHeartbeatServiceImpl extends TProcessHeartbeatServiceGrpc.TProcessHeartbeatServiceImplBase {

    private final ProcessQueueDao queueDao;

    @Inject
    public ProcessHeartbeatServiceImpl(ProcessQueueDao queueDao) {
        this.queueDao = queueDao;
    }

    @Override
    public void heartbeat(TProcessHeartbeatRequest request, StreamObserver<Empty> responseObserver) {
        String s = request.getInstanceId();
        UUID instanceId = UUID.fromString(s);

        if (!queueDao.touch(instanceId)) {
            responseObserver.onError(new IllegalArgumentException("Process not found: " + s));
            return;
        }

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
