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

import com.google.protobuf.ByteString;
import com.walmartlabs.concord.common.secret.BinaryDataSecret;
import com.walmartlabs.concord.common.secret.KeyPair;
import com.walmartlabs.concord.common.secret.UsernamePassword;
import com.walmartlabs.concord.rpc.TSecretReaderServiceGrpc.TSecretReaderServiceBlockingStub;
import com.walmartlabs.concord.sdk.ClientException;
import com.walmartlabs.concord.sdk.Secret;
import com.walmartlabs.concord.sdk.SecretReaderService;
import io.grpc.ManagedChannel;

import javax.xml.bind.DatatypeConverter;
import java.util.concurrent.TimeUnit;

public class SecretReaderServiceImpl implements SecretReaderService {

    private static final long FETCH_TIMEOUT = 5000;
    private static final long UPDATE_TIMEOUT = 5000;

    private final ManagedChannel channel;

    public SecretReaderServiceImpl(ManagedChannel channel) {
        this.channel = channel;
    }

    @Override
    public Secret fetch(String instanceId, String orgName, String secretName, String password) throws ClientException {
        TSecretReaderServiceBlockingStub blockingStub = TSecretReaderServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(FETCH_TIMEOUT, TimeUnit.MILLISECONDS);

        TFetchSecretRequest.Builder req = TFetchSecretRequest.newBuilder()
                .setInstanceId(instanceId)
                .setSecretName(secretName);

        if (password != null){
            req.setPassword(password);
        }

        if (orgName != null) {
            req.setOrgName(orgName);
        }

        TFetchSecretResponse resp = blockingStub.fetch(req.build());

        TFetchSecretStatus status = resp.getStatus();
        if (status == TFetchSecretStatus.SECRET_NOT_FOUND) {
            return null;
        }

        if (status != TFetchSecretStatus.SECRET_FOUND) {
            throw new ClientException("Error while trying to fetch a secret: " + status);
        }

        byte[] ab = resp.getData().toByteArray();

        switch (resp.getType()) {
            case DATA: {
                return new BinaryDataSecret(ab);
            }
            case KEY_PAIR: {
                return KeyPair.deserialize(ab);
            }
            case USERNAME_PASSWORD: {
                return UsernamePassword.deserialize(ab);
            }
            default: {
                throw new ClientException("Unsupported secret type: " + resp.getType());
            }
        }
    }

    @Override
    public String decryptString(String instanceId, String s) throws ClientException {
        byte[] input = DatatypeConverter.parseBase64Binary(s);

        TSecretReaderServiceBlockingStub blockingStub = TSecretReaderServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(UPDATE_TIMEOUT, TimeUnit.MILLISECONDS);

        TDecryptResponse resp = blockingStub.decrypt(TDecryptRequest.newBuilder()
                .setInstanceId(instanceId)
                .setData(ByteString.copyFrom(input))
                .build());

        if (resp.getStatus() != TDecryptStatus.KEY_FOUND) {
            throw new ClientException("Error while trying to decrypt a value: " + resp.getStatus());
        }

        byte[] result = resp.getData().toByteArray();
        return new String(result);
    }
}
