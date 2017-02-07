package com.walmartlabs.concord.server.security.secret;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.security.GeneralSecurityException;
import java.util.function.Consumer;

public class KeyPair implements Serializable {

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
        byte[] privateKey = array(out -> k.writePrivateKey(out));

        return new KeyPair(publicKey, privateKey);
    }

    public static KeyPair decrypt(byte[] input, byte[] password, byte[] hash) throws SecurityException {
        try {
            byte[] ab = CryptoUtils.decrypt(input, password, hash);
            return deserialize(ab);
        } catch (GeneralSecurityException e) {
            throw new SecurityException("Error decrypting a key pair", e);
        }
    }

    public static byte[] encrypt(KeyPair k, byte[] password, byte[] hash) throws SecurityException {
        try {
            return CryptoUtils.encrypt(serialize(k), password, hash);
        } catch (GeneralSecurityException e) {
            throw new SecurityException("Error encrypting a key pair", e);
        }
    }

    private static KeyPair deserialize(byte[] input) {
        ByteArrayDataInput in = ByteStreams.newDataInput(input);

        int n1 = assertKeyLength(in.readInt());
        byte[] ab1 = new byte[n1];
        in.readFully(ab1);

        int n2 = assertKeyLength(in.readInt());
        byte[] ab2 = new byte[n2];
        in.readFully(ab2);

        return new KeyPair(ab1, ab2);
    }

    private static byte[] serialize(KeyPair k) {
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
