package com.walmartlabs.concord.rpc;

import com.google.protobuf.ByteString;
import com.walmartlabs.concord.rpc.TSecretStoreServiceGrpc.TSecretStoreServiceBlockingStub;
import io.grpc.ManagedChannel;

import javax.xml.bind.DatatypeConverter;
import java.util.concurrent.TimeUnit;

public class SecretStoreServiceImpl implements SecretStoreService {

    private static final long UPDATE_TIMEOUT = 5000;

    private final ManagedChannel channel;

    public SecretStoreServiceImpl(ManagedChannel channel) {
        this.channel = channel;
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

        if (resp.getStatus() != TDecryptStatus.SUCCESS) {
            throw new ClientException("Error while trying to decrypt a value: " + TDecryptStatus.KEY_NOT_FOUND);
        }

        byte[] result = resp.getData().toByteArray();
        return new String(result);
    }
}
