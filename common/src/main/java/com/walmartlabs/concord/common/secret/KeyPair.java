package com.walmartlabs.concord.common.secret;

import com.walmartlabs.concord.sdk.Secret;

import java.io.*;

public class KeyPair implements Secret {

    public static KeyPair deserialize(byte[] input) {
        try {
            DataInput in = new DataInputStream(new ByteArrayInputStream(input));

            int n1 = assertKeyLength(in.readInt());
            byte[] ab1 = new byte[n1];
            in.readFully(ab1);

            int n2 = assertKeyLength(in.readInt());
            byte[] ab2 = new byte[n2];
            in.readFully(ab2);

            return new KeyPair(ab1, ab2);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] serialize(KeyPair k) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutput out = new DataOutputStream(baos);

            out.writeInt(k.getPublicKey().length);
            out.write(k.getPublicKey());

            out.writeInt(k.getPrivateKey().length);
            out.write(k.getPrivateKey());

            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static int assertKeyLength(int n) {
        if (n < 0 || n > 8192) {
            throw new IllegalArgumentException("Invalid key length: " + n);
        }
        return n;
    }

    private final byte[] publicKey;
    private final byte[] privateKey;

    public KeyPair(byte[] publicKey, byte[] privateKey) {
        this.publicKey = publicKey;
        this.privateKey = privateKey;
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

    public byte[] getPrivateKey() {
        return privateKey;
    }
}
