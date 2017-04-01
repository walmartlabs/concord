package com.walmartlabs.concord.server.security.secret;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Consumer;

public class KeyPair implements Secret {

    private static final int DEFAULT_KEY_SIZE = 2048;
    private static final int DEFAULT_KEY_TYPE = com.jcraft.jsch.KeyPair.RSA;
    private static final String DEFAULT_KEY_COMMENT = "concord-server";

    private static final JSch jsch = new JSch();

    public static KeyPair create() {
        com.jcraft.jsch.KeyPair k;
        synchronized (jsch) {
            try {
                k = com.jcraft.jsch.KeyPair.genKeyPair(jsch, DEFAULT_KEY_TYPE, DEFAULT_KEY_SIZE);
            } catch (JSchException e) {
                throw new SecurityException(e);
            }
        }

        byte[] publicKey = array(out -> k.writePublicKey(out, DEFAULT_KEY_COMMENT));
        byte[] privateKey = array(k::writePrivateKey);

        return new KeyPair(publicKey, privateKey);
    }

    public static KeyPair create(InputStream publicIn, InputStream privateIn) throws IOException {
        byte[] publicKey = ByteStreams.toByteArray(publicIn);
        byte[] privateKey = ByteStreams.toByteArray(privateIn);

        return new KeyPair(publicKey, privateKey);
    }

    public static KeyPair deserialize(byte[] input) {
        ByteArrayDataInput in = ByteStreams.newDataInput(input);

        int n1 = assertKeyLength(in.readInt());
        byte[] ab1 = new byte[n1];
        in.readFully(ab1);

        int n2 = assertKeyLength(in.readInt());
        byte[] ab2 = new byte[n2];
        in.readFully(ab2);

        return new KeyPair(ab1, ab2);
    }

    public static byte[] serialize(KeyPair k) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();

        out.writeInt(k.getPublicKey().length);
        out.write(k.getPublicKey());

        out.writeInt(k.getPrivateKey().length);
        out.write(k.getPrivateKey());

        return out.toByteArray();
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

    private static byte[] array(Consumer<OutputStream> c) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        c.accept(out);
        return out.toByteArray();
    }
}
