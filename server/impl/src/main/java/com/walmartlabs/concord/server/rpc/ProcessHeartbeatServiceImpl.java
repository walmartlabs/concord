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
