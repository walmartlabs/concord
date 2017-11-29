package com.walmartlabs.concord.server.rpc;

import com.google.protobuf.ByteString;
import com.walmartlabs.concord.rpc.*;
import com.walmartlabs.concord.sdk.SecretStoreService;
import com.walmartlabs.concord.server.api.org.secret.SecretType;
import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.secret.SecretDao.SecretDataEntry;
import com.walmartlabs.concord.server.org.secret.SecretManager;
import com.walmartlabs.concord.server.process.ProcessSecurityContext;
import com.walmartlabs.concord.server.process.logs.LogManager;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import io.grpc.stub.StreamObserver;
import org.apache.shiro.authz.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.IllegalBlockSizeException;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.UUID;

@Named
@Singleton
public class SecretStoreServiceImpl extends TSecretStoreServiceGrpc.TSecretStoreServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(SecretStoreService.class);

    private final SecretManager secretManager;
    private final ProcessQueueDao queueDao;
    private final ProcessSecurityContext securityContext;
    private final LogManager logManager;

    @Inject
    public SecretStoreServiceImpl(SecretManager secretManager,
                                  ProcessQueueDao queueDao,
                                  ProcessSecurityContext securityContext,
                                  LogManager logManager) {

        this.secretManager = secretManager;
        this.queueDao = queueDao;
        this.securityContext = securityContext;
        this.logManager = logManager;
    }

    @Override
    public void fetch(TFetchSecretRequest request, StreamObserver<TFetchSecretResponse> responseObserver) {
        String sId = request.getInstanceId();

        String secretName = request.getSecretName();
        if (secretName == null) {
            responseObserver.onError(new IllegalArgumentException("Secret name is required"));
            return;
        }

        String secretPassword = request.getPassword();
        if (secretPassword == null) {
            responseObserver.onError(new IllegalArgumentException("Secret's password is required"));
            return;
        }

        UUID instanceId = UUID.fromString(sId);
        UUID orgId = getOrgId(instanceId, secretName);

        try {
            securityContext.runAsInitiator(instanceId, () -> {
                SecretDataEntry entry = secretManager.getRaw(orgId, secretName, secretPassword);
                if (entry == null) {
                    responseObserver.onNext(TFetchSecretResponse.newBuilder()
                            .setStatus(TFetchSecretStatus.SECRET_NOT_FOUND)
                            .build());
                    responseObserver.onCompleted();
                    return null;
                }

                responseObserver.onNext(TFetchSecretResponse.newBuilder()
                        .setType(convert(entry.getType()))
                        .setData(ByteString.copyFrom(entry.getData()))
                        .build());
                responseObserver.onCompleted();

                return null;
            });
        } catch (UnauthorizedException e) {
            responseObserver.onNext(TFetchSecretResponse.newBuilder()
                    .setStatus(TFetchSecretStatus.SECRET_NOT_AUTHORIZED)
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.warn("fetch ['{}'] -> error while fetching a secret", secretName, e);
            responseObserver.onNext(TFetchSecretResponse.newBuilder()
                    .setStatus(TFetchSecretStatus.SECRET_NOT_AUTHORIZED)
                    .build());
            responseObserver.onCompleted();
            responseObserver.onError(e);
        }
    }

    @Override
    public void decrypt(TDecryptRequest request, StreamObserver<TDecryptResponse> responseObserver) {
        String instanceId = request.getInstanceId();

        Optional<String> projectName = getProjectName(instanceId);
        if (!projectName.isPresent()) {
            responseObserver.onNext(TDecryptResponse.newBuilder()
                    .setStatus(TDecryptStatus.KEY_NOT_FOUND)
                    .build());
            responseObserver.onCompleted();
            return;
        }

        byte[] data = request.getData().toByteArray();

        byte[] result;
        try {
            result = secretManager.decryptData(projectName.get(), data);
        } catch (Exception e) {
            handleError(e, responseObserver);
            return;
        }

        responseObserver.onNext(TDecryptResponse.newBuilder()
                .setStatus(TDecryptStatus.KEY_FOUND)
                .setData(ByteString.copyFrom(result))
                .build());
        responseObserver.onCompleted();
    }

    private void handleError(Exception e, StreamObserver<TDecryptResponse> responseObserver) {
        Throwable cause = e.getCause();
        if (cause instanceof IllegalBlockSizeException) {
            responseObserver.onNext(TDecryptResponse.newBuilder()
                    .setStatus(TDecryptStatus.INVALID_DATA)
                    .build());
            responseObserver.onCompleted();
        } else {
            responseObserver.onError(e);
        }
    }

    private UUID getOrgId(UUID instanceId, String secretName) {
        UUID id = queueDao.getOrgId(instanceId);
        if (id == null) {
            logManager.warn(instanceId, "Using a secret from the default organization: {}. " +
                    "Consider moving those secrets into your organization.", secretName);
            return OrganizationManager.DEFAULT_ORG_ID;
        }
        return id;
    }

    private Optional<String> getProjectName(String instanceId) {
        ProcessEntry entry = queueDao.get(UUID.fromString(instanceId));
        if (entry == null) {
            return Optional.empty();
        }

        String projectName = entry.getProjectName();
        if (projectName == null) {
            return Optional.empty();
        }

        return Optional.of(projectName);
    }

    private static TSecretType convert(SecretType t) {
        if (t == null) {
            return null;
        }

        switch (t) {
            case DATA:
                return TSecretType.DATA;
            case KEY_PAIR:
                return TSecretType.KEY_PAIR;
            case USERNAME_PASSWORD:
                return TSecretType.USERNAME_PASSWORD;
            default:
                throw new IllegalArgumentException("Unsupported secret type: " + t);
        }
    }
}
