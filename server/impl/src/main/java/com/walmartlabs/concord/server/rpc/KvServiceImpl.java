package com.walmartlabs.concord.server.rpc;

import com.google.protobuf.Empty;
import com.walmartlabs.concord.rpc.*;
import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.project.kv.KvDao;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Optional;

@Named
public class KvServiceImpl extends TKvServiceGrpc.TKvServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(KvServiceImpl.class);
    private static final String DEFAULT_PROJECT_NAME = "__no_project__";

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
        Optional<String> projectName = assertProjectName(instanceId);

        if (!projectName.isPresent()) {
            responseObserver.onError(new IllegalArgumentException("Process instance not found: " + instanceId));
            return;
        }

        String key = request.getKey();
        kvDao.remove(projectName.get(), key);
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    @WithTimer
    public void put(TKvPutStringRequest request, StreamObserver<Empty> responseObserver) {
        String instanceId = request.getInstanceId();
        Optional<String> projectName = assertProjectName(instanceId);

        if (!projectName.isPresent()) {
            responseObserver.onError(new IllegalArgumentException("Process instance not found: " + instanceId));
            return;
        }

        String key = request.getKey();
        String value = request.getValue();

        kvDao.put(projectName.get(), key, value);

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    @WithTimer
    public void get(TKvGetStringRequest request, StreamObserver<TKvGetStringResponse> responseObserver) {
        String instanceId = request.getInstanceId();
        Optional<String> projectName = assertProjectName(instanceId);

        if (!projectName.isPresent()) {
            responseObserver.onError(new IllegalArgumentException("Process instance not found: " + instanceId));
            return;
        }

        String key = request.getKey();

        String value = kvDao.getString(projectName.get(), key);
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
        Optional<String> projectName = assertProjectName(instanceId);

        if (!projectName.isPresent()) {
            responseObserver.onError(new IllegalArgumentException("Process instance not found: " + instanceId));
            return;
        }

        String key = request.getKey();

        long i = kvDao.inc(projectName.get(), key);
        responseObserver.onNext(TKvIncResponse.newBuilder()
                .setResult(i)
                .build());

        responseObserver.onCompleted();
    }

    private Optional<String> assertProjectName(String instanceId) {
        ProcessEntry entry = queueDao.get(instanceId);
        if (entry == null) {
            return Optional.empty();
        }

        String projectName = entry.getProjectName();

        if (projectName == null) {
            log.warn("assertProjectName ['{}'] -> no project found, using the default value", instanceId, projectName);
            projectName = DEFAULT_PROJECT_NAME;
        }

        return Optional.of(projectName);
    }
}
