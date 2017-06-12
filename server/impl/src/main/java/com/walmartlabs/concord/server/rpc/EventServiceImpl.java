package com.walmartlabs.concord.server.rpc;

import com.google.protobuf.Empty;
import com.google.protobuf.util.Timestamps;
import com.walmartlabs.concord.rpc.TEventRequest;
import com.walmartlabs.concord.rpc.TEventServiceGrpc;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.project.event.EventDao;
import io.grpc.stub.StreamObserver;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Date;

@Named
public class EventServiceImpl extends TEventServiceGrpc.TEventServiceImplBase {

    private final EventDao eventDao;

    @Inject
    public EventServiceImpl(EventDao eventDao) {
        this.eventDao = eventDao;
    }

    @Override
    @WithTimer
    public void onEvent(TEventRequest request, StreamObserver<Empty> responseObserver) {
        byte[] data = request.getData().toByteArray();
        Date eventDate = new Date(Timestamps.toMillis(request.getDate()));

        eventDao.insert(request.getInstanceId(), request.getType(), eventDate, new String(data));

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
