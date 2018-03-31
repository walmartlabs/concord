package com.walmartlabs.concord.server.rpc;

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

import com.google.protobuf.Empty;
import com.google.protobuf.util.Timestamps;
import com.walmartlabs.concord.rpc.TEventRequest;
import com.walmartlabs.concord.rpc.TEventServiceGrpc;
import com.walmartlabs.concord.rpc.TEventType;
import com.walmartlabs.concord.server.api.process.ProcessEventRequest;
import com.walmartlabs.concord.server.api.process.ProcessEventType;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.process.event.EventDao;
import io.grpc.stub.StreamObserver;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Date;
import java.util.UUID;

/**
 * @deprecated in favor of {@link com.walmartlabs.concord.server.api.process.ProcessEventResource#event(UUID, ProcessEventRequest)}
 */
@Named
@Deprecated
public class EventServiceImpl extends TEventServiceGrpc.TEventServiceImplBase {

    private final EventDao eventDao;

    @Inject
    public EventServiceImpl(EventDao eventDao) {
        this.eventDao = eventDao;
    }

    @Override
    @WithTimer
    public void onEvent(TEventRequest request, StreamObserver<Empty> responseObserver) {
        UUID instanceId = UUID.fromString(request.getInstanceId());
        byte[] data = request.getData().toByteArray();

        eventDao.insert(instanceId, convert(request.getType()), new String(data));

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    private static ProcessEventType convert(TEventType type) {
        switch (type) {
            case PROCESS_ELEMENT:
                return ProcessEventType.ELEMENT;
            case ANSIBLE_EVENT:
                return ProcessEventType.ANSIBLE;
            default:
                throw new IllegalArgumentException("Unsupported event type: " + type);
        }
    }
}
