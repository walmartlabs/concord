package com.walmartlabs.concord.rpc;

import com.walmartlabs.concord.rpc.TKvServiceGrpc.TKvServiceBlockingStub;
import io.grpc.ManagedChannel;

import java.util.concurrent.TimeUnit;

public class KvServiceImpl implements KvService {

    private static final long UPDATE_TIMEOUT = 5000;

    private final ManagedChannel channel;

    public KvServiceImpl(ManagedChannel channel) {
        this.channel = channel;
    }

    @Override
    public void remove(String instanceId, String key) throws ClientException {
        TKvServiceBlockingStub blockingStub = TKvServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(UPDATE_TIMEOUT, TimeUnit.MILLISECONDS);

        blockingStub.remove(TKvRemoveRequest.newBuilder()
                .setInstanceId(instanceId)
                .setKey(key)
                .build());
    }

    @Override
    public void put(String instanceId, String key, String value) throws ClientException {
        if (value == null) {
            throw new IllegalArgumentException("KV store: null values are not allowed. Got: " + key + " = " + value);
        }

        TKvServiceBlockingStub blockingStub = TKvServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(UPDATE_TIMEOUT, TimeUnit.MILLISECONDS);

        TKvPutStringRequest req = TKvPutStringRequest.newBuilder()
                .setInstanceId(instanceId)
                .setKey(key)
                .setValue(value)
                .build();

        blockingStub.put(req);
    }

    @Override
    public String get(String instanceId, String key) throws ClientException {
        TKvServiceBlockingStub blockingStub = TKvServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(UPDATE_TIMEOUT, TimeUnit.MILLISECONDS);

        TKvGetStringRequest req = TKvGetStringRequest.newBuilder()
                .setInstanceId(instanceId)
                .setKey(key)
                .build();

        TKvGetStringResponse resp = blockingStub.get(req);
        String value = resp.getValue();

        if (value == null || value.isEmpty()) {
            return null;
        }

        return value;
    }

    @Override
    public long inc(String instanceId, String key) throws ClientException {
        TKvServiceBlockingStub blockingStub = TKvServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(UPDATE_TIMEOUT, TimeUnit.MILLISECONDS);

        TKvIncRequest req = TKvIncRequest.newBuilder()
                .setInstanceId(instanceId)
                .setKey(key)
                .build();

        TKvIncResponse resp = blockingStub.inc(req);
        return resp.getResult();
    }
}
