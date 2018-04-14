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
import com.walmartlabs.concord.rpc.*;
import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.org.project.KvDao;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Optional;
import java.util.UUID;

@Named
public class KvServiceImpl extends TKvServiceGrpc.TKvServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(KvServiceImpl.class);
    private static final UUID DEFAULT_PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final ProcessQueueDao queueDao;
    private final KvDao kvDao;

    @Inject
    public KvServiceImpl(ProcessQueueDao queueDao, KvDao kvDao) {
        this.queueDao = queueDao;
        this.kvDao = kvDao;
    }

    @Override
    public void remove(TKvRemoveRequest request, StreamObserver<Empty> responseObserver) {
        String instanceId = request.getInstanceId();
        Optional<UUID> projectId = assertProjectId(instanceId);

        if (!projectId.isPresent()) {
            responseObserver.onError(new IllegalArgumentException("Process instance not found: " + instanceId));
            return;
        }

        String key = request.getKey();
        kvDao.remove(projectId.get(), key);
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void putString(TKvPutStringRequest request, StreamObserver<Empty> responseObserver) {
        String instanceId = request.getInstanceId();
        Optional<UUID> projectId = assertProjectId(instanceId);

        if (!projectId.isPresent()) {
            responseObserver.onError(new IllegalArgumentException("Process instance not found: " + instanceId));
            return;
        }

        String key = request.getKey();
        String value = request.getValue();

        kvDao.putString(projectId.get(), key, value);

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void getString(TKvGetStringRequest request, StreamObserver<TKvGetStringResponse> responseObserver) {
        String instanceId = request.getInstanceId();
        Optional<UUID> projectId = assertProjectId(instanceId);

        if (!projectId.isPresent()) {
            responseObserver.onError(new IllegalArgumentException("Process instance not found: " + instanceId));
            return;
        }

        String key = request.getKey();

        String value = kvDao.getString(projectId.get(), key);
        if (value == null) {
            responseObserver.onNext(TKvGetStringResponse.newBuilder().build());
        } else {
            responseObserver.onNext(TKvGetStringResponse.newBuilder()
                    .setValue(value)
                    .build());
        }

        responseObserver.onCompleted();
    }

    @Override
    public void incLong(TKvIncRequest request, StreamObserver<TKvIncResponse> responseObserver) {
        String instanceId = request.getInstanceId();
        Optional<UUID> projectId = assertProjectId(instanceId);

        if (!projectId.isPresent()) {
            responseObserver.onError(new IllegalArgumentException("Process instance not found: " + instanceId));
            return;
        }

        String key = request.getKey();

        long i = kvDao.inc(projectId.get(), key);
        responseObserver.onNext(TKvIncResponse.newBuilder()
                .setResult(i)
                .build());

        responseObserver.onCompleted();
    }

    @Override
    public void getLong(TKvGetLongRequest request, StreamObserver<TKvGetLongResponse> responseObserver) {
        String instanceId = request.getInstanceId();
        Optional<UUID> projectId = assertProjectId(instanceId);

        if (!projectId.isPresent()) {
            responseObserver.onError(new IllegalArgumentException("Process instance not found: " + instanceId));
            return;
        }

        String key = request.getKey();

        Long value = kvDao.getLong(projectId.get(), key);
        if (value == null) {
            responseObserver.onNext(TKvGetLongResponse.newBuilder()
                    .setHasValue(false)
                    .build());
        } else {
            responseObserver.onNext(TKvGetLongResponse.newBuilder()
                    .setValue(value)
                    .setHasValue(true)
                    .build());
        }

        responseObserver.onCompleted();
    }

    @Override
    public void putLong(TKvPutLongRequest request, StreamObserver<Empty> responseObserver) {
        String instanceId = request.getInstanceId();
        Optional<UUID> projectId = assertProjectId(instanceId);

        if (!projectId.isPresent()) {
            responseObserver.onError(new IllegalArgumentException("Process instance not found: " + instanceId));
            return;
        }

        String key = request.getKey();
        long value = request.getValue();

        kvDao.putLong(projectId.get(), key, value);

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    private Optional<UUID> assertProjectId(String instanceId) {
        ProcessEntry entry = queueDao.get(UUID.fromString(instanceId));
        if (entry == null) {
            return Optional.empty();
        }

        UUID projectId = entry.getProjectId();
        if (projectId == null) {
            log.warn("assertProjectId ['{}'] -> no project found, using the default value", instanceId);
            projectId = DEFAULT_PROJECT_ID;
        }

        return Optional.of(projectId);
    }
}
