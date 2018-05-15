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

import com.google.common.base.Strings;
import com.google.protobuf.ByteString;
import com.walmartlabs.concord.rpc.*;
import com.walmartlabs.concord.sdk.SecretReaderService;
import com.walmartlabs.concord.server.api.org.secret.SecretType;
import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.org.OrganizationDao;
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

@Deprecated
@Named
@Singleton
public class SecretReaderServiceImpl extends TSecretReaderServiceGrpc.TSecretReaderServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(SecretReaderService.class);

    private final SecretManager secretManager;
    private final OrganizationDao orgDao;
    private final ProcessQueueDao queueDao;
    private final ProcessSecurityContext securityContext;
    private final LogManager logManager;

    @Inject
    public SecretReaderServiceImpl(SecretManager secretManager,
                                   OrganizationDao orgDao, ProcessQueueDao queueDao,
                                   ProcessSecurityContext securityContext,
                                   LogManager logManager) {

        this.secretManager = secretManager;
        this.orgDao = orgDao;
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

        // request.password, when not provided, comes as empty string and needs to be converted into null value
        String secretPassword  = Strings.isNullOrEmpty(request.getPassword()) ? null : request.getPassword();

        UUID instanceId = UUID.fromString(sId);

        String orgName = request.getOrgName();
        if (orgName != null && orgName.trim().isEmpty()) {
            orgName = null;
        }

        UUID orgId = getOrgId(instanceId, orgName, secretName);

        try {
            securityContext.runAs(instanceId, () -> {
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

    private UUID getOrgId(UUID instanceId, String orgName, String secretName) {
        UUID id = null;

        if (orgName != null) {
            id = orgDao.getId(orgName);
            if (id == null) {
                logManager.error(instanceId, "Error while exporting a secret - organization not found: ", orgName);
                throw new IllegalArgumentException("Organization '" + orgName + "' not found");
            }
        }

        if (id == null) {
            id = queueDao.getOrgId(instanceId);
        }

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