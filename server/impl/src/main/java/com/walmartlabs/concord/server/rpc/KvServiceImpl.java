package com.walmartlabs.concord.server.rpc;

import com.google.protobuf.Empty;
import com.walmartlabs.concord.rpc.*;
import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.project.kv.KvDao;
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
    @WithTimer
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
    @WithTimer
    public void put(TKvPutStringRequest request, StreamObserver<Empty> responseObserver) {
        String instanceId = request.getInstanceId();
        Optional<UUID> projectId = assertProjectId(instanceId);

        if (!projectId.isPresent()) {
            responseObserver.onError(new IllegalArgumentException("Process instance not found: " + instanceId));
            return;
        }

        String key = request.getKey();
        String value = request.getValue();

        kvDao.put(projectId.get(), key, value);

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    @WithTimer
    public void get(TKvGetStringRequest request, StreamObserver<TKvGetStringResponse> responseObserver) {
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
    @WithTimer
    public void inc(TKvIncRequest request, StreamObserver<TKvIncResponse> responseObserver) {
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
