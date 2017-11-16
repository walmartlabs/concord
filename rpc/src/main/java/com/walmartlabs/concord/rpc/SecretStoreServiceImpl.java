package com.walmartlabs.concord.rpc;

import com.google.protobuf.ByteString;
import com.walmartlabs.concord.common.secret.BinaryDataSecret;
import com.walmartlabs.concord.common.secret.KeyPair;
import com.walmartlabs.concord.common.secret.UsernamePassword;
import com.walmartlabs.concord.rpc.TSecretStoreServiceGrpc.TSecretStoreServiceBlockingStub;
import com.walmartlabs.concord.sdk.ClientException;
import com.walmartlabs.concord.sdk.Secret;
import com.walmartlabs.concord.sdk.SecretStoreService;
import io.grpc.ManagedChannel;

import javax.xml.bind.DatatypeConverter;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SecretStoreServiceImpl implements SecretStoreService {

    private static final long FETCH_TIMEOUT = 5000;
    private static final long UPDATE_TIMEOUT = 5000;

    private final ManagedChannel channel;

    public SecretStoreServiceImpl(ManagedChannel channel) {
        this.channel = channel;
    }

    @Override
    public Secret fetch(String instanceId, String secretName, String password) throws ClientException {
        TSecretStoreServiceBlockingStub blockingStub = TSecretStoreServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(FETCH_TIMEOUT, TimeUnit.MILLISECONDS);

        TFetchSecretResponse resp = blockingStub.fetch(TFetchSecretRequest.newBuilder()
                .setInstanceId(instanceId)
                .setSecretName(secretName)
                .setPassword(password)
                .build());

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

        TSecretStoreServiceBlockingStub blockingStub = TSecretStoreServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(UPDATE_TIMEOUT, TimeUnit.MILLISECONDS);

        TDecryptResponse resp = blockingStub.decrypt(TDecryptRequest.newBuilder()
                .setInstanceId(instanceId)
                .setData(ByteString.copyFrom(input))
                .build());

        if (resp.getStatus() != TDecryptStatus.KEY_FOUND) {
            throw new ClientException("Error while trying to decrypt a value: " + TDecryptStatus.KEY_NOT_FOUND);
        }

        byte[] result = resp.getData().toByteArray();
        return new String(result);
    }
}
