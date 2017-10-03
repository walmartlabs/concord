package com.walmartlabs.concord.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import io.grpc.ManagedChannel;

import java.io.Serializable;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class EventServiceImpl implements EventService {

    private static final long REQUEST_TIMEOUT = 5000;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ManagedChannel channel;

    public EventServiceImpl(ManagedChannel channel) {
        this.channel = channel;
    }

    @Override
    public void onEvent(String instanceId, Date date, EventType type, Serializable data) throws ClientException {
        byte[] jsonData;
        try {
            jsonData = objectMapper.writeValueAsBytes(data);
        } catch (Exception e) {
            throw new IllegalArgumentException("onEvent error: " + e.getMessage());
        }

        Instant time = Instant.now();

        TEventServiceGrpc.TEventServiceBlockingStub blockingStub = TEventServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(REQUEST_TIMEOUT, TimeUnit.MILLISECONDS);

        blockingStub.onEvent(TEventRequest.newBuilder()
                .setInstanceId(instanceId)
                .setType(convert(type))
                .setDate(Timestamp.newBuilder().setSeconds(time.getEpochSecond())
                        .setNanos(time.getNano()).build())
                .setData(ByteString.copyFrom(jsonData))
            .build());
    }

    private TEventType convert(EventType type) {
        switch (type) {
            case PROCESS_ELEMENT:
                return TEventType.PROCESS_ELEMENT;
            case ANSIBLE:
                return TEventType.ANSIBLE_EVENT;
            default:
                throw new IllegalArgumentException("Unsupported event type: " + type);
        }
    }
}
