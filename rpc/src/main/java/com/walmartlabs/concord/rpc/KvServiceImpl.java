package com.walmartlabs.concord.rpc;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.walmartlabs.concord.rpc.TKvServiceGrpc.TKvServiceBlockingStub;
import com.walmartlabs.concord.sdk.ClientException;
import com.walmartlabs.concord.sdk.KvService;
import io.grpc.ManagedChannel;

import java.util.concurrent.TimeUnit;

public class KvServiceImpl implements KvService {

    private static final long UPDATE_TIMEOUT = 5000;
    private static final long READ_TIMEOUT = 5000;

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
    public void putString(String instanceId, String key, String value) throws ClientException {
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

        blockingStub.putString(req);
    }

    @Override
    public String getString(String instanceId, String key) throws ClientException {
        TKvServiceBlockingStub blockingStub = TKvServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(READ_TIMEOUT, TimeUnit.MILLISECONDS);

        TKvGetStringRequest req = TKvGetStringRequest.newBuilder()
                .setInstanceId(instanceId)
                .setKey(key)
                .build();

        TKvGetStringResponse resp = blockingStub.getString(req);
        String value = resp.getValue();

        if (value == null || value.isEmpty()) {
            return null;
        }

        return value;
    }

    @Override
    public long incLong(String instanceId, String key) throws ClientException {
        TKvServiceBlockingStub blockingStub = TKvServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(UPDATE_TIMEOUT, TimeUnit.MILLISECONDS);

        TKvIncRequest req = TKvIncRequest.newBuilder()
                .setInstanceId(instanceId)
                .setKey(key)
                .build();

        TKvIncResponse resp = blockingStub.incLong(req);
        return resp.getResult();
    }

    @Override
    public void putLong(String instanceId, String key, Long value) throws ClientException {
        if (value == null) {
            throw new IllegalArgumentException("KV store: null values are not allowed. Got: " + key + " = " + value);
        }

        TKvServiceBlockingStub blockingStub = TKvServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(UPDATE_TIMEOUT, TimeUnit.MILLISECONDS);

        TKvPutLongRequest req = TKvPutLongRequest.newBuilder()
                .setInstanceId(instanceId)
                .setKey(key)
                .setValue(value)
                .build();

        blockingStub.putLong(req);
    }

    @Override
    public Long getLong(String instanceId, String key) throws ClientException {
        TKvServiceBlockingStub blockingStub = TKvServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(READ_TIMEOUT, TimeUnit.MILLISECONDS);

        TKvGetLongRequest req = TKvGetLongRequest.newBuilder()
                .setInstanceId(instanceId)
                .setKey(key)
                .build();

        TKvGetLongResponse resp = blockingStub.getLong(req);
        if (!resp.getHasValue()) {
            return null;
        }

        long value = resp.getValue();
        return value;
    }
}
