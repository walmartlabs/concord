package com.walmartlabs.concord.server.rpc;

import com.google.protobuf.ByteString;
import com.walmartlabs.concord.rpc.TDecryptRequest;
import com.walmartlabs.concord.rpc.TDecryptResponse;
import com.walmartlabs.concord.rpc.TDecryptStatus;
import com.walmartlabs.concord.rpc.TSecretStoreServiceGrpc;
import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.project.ProjectSecretManager;
import io.grpc.stub.StreamObserver;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Optional;

@Named
public class SecretStoreServiceImpl extends TSecretStoreServiceGrpc.TSecretStoreServiceImplBase {

    private final ProjectSecretManager projectSecretManager;
    private final ProcessQueueDao queueDao;

    @Inject
    public SecretStoreServiceImpl(ProjectSecretManager projectSecretManager, ProcessQueueDao queueDao) {
        this.projectSecretManager = projectSecretManager;
        this.queueDao = queueDao;
    }

    @Override
    public void decrypt(TDecryptRequest request, StreamObserver<TDecryptResponse> responseObserver) {
        String instanceId = request.getInstanceId();

        Optional<String> projectName = assertProjectName(instanceId);
        if (!projectName.isPresent()) {
            responseObserver.onNext(TDecryptResponse.newBuilder()
                    .setStatus(TDecryptStatus.KEY_NOT_FOUND)
                    .build());
            responseObserver.onCompleted();
            return;
        }

        byte[] data = request.getData().toByteArray();
        byte[] result = projectSecretManager.decrypt(projectName.get(), data);

        responseObserver.onNext(TDecryptResponse.newBuilder()
                .setStatus(TDecryptStatus.SUCCESS)
                .setData(ByteString.copyFrom(result))
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
            return Optional.empty();
        }

        return Optional.of(projectName);
    }
}
