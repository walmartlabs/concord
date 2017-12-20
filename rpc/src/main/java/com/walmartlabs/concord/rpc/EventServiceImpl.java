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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import com.walmartlabs.concord.sdk.ClientException;
import com.walmartlabs.concord.sdk.EventService;
import com.walmartlabs.concord.sdk.EventType;
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
