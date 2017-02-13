package com.walmartlabs.concord.server.security.secret;

import com.walmartlabs.concord.server.api.security.secret.SecretType;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.Function;

public final class SecretUtils {

    public static <T extends Secret> T decrypt(Function<byte[], T> deserializer, byte[] input, byte[] password, byte[] hash) throws SecurityException {
        try {
            byte[] ab = decrypt(input, password, hash);
            return deserializer.apply(ab);
        } catch (GeneralSecurityException e) {
            throw new SecurityException("Error decrypting a key pair", e);
        }
    }

    public static Secret decrypt(SecretType type, byte[] input, byte[] password, byte[] salt) {
        Function<byte[], ? extends Secret> deserializer;
        switch (type) {
            case KEY_PAIR:
                deserializer = KeyPair::deserialize;
                break;
            case USERNAME_PASSWORD:
                deserializer = UsernamePassword::deserialize;
                break;
            default:
                throw new IllegalArgumentException("Unknown secret type: " + type);
        }
        return decrypt(deserializer, input, password, salt);
    }

    public static <T extends Secret> byte[] encrypt(Function<T, byte[]> serializer, T k, byte[] password, byte[] hash) throws SecurityException {
        try {
            return encrypt(serializer.apply(k), password, hash);
        } catch (GeneralSecurityException e) {
            throw new SecurityException("Error encrypting a key pair", e);
        }
    }

    private static byte[] encrypt(byte[] input, byte[] password, byte[] salt) throws GeneralSecurityException {
        Cipher c = init(password, salt, Cipher.ENCRYPT_MODE);
        return c.doFinal(input);
    }

    private static byte[] decrypt(byte[] input, byte[] password, byte[] salt) throws GeneralSecurityException {
        Cipher c = init(password, salt, Cipher.DECRYPT_MODE);
        return c.doFinal(input);
    }

    private static byte[] hash(byte[] in, byte[] salt) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        digest.update(salt);
        return digest.digest(in);
    }

    private static Cipher init(byte[] password, byte[] salt, int mode) throws GeneralSecurityException {
        // TODO we probably want AES-256, but that requires JCE extensions
        Cipher c = Cipher.getInstance("AES");

        byte[] key = hash(password, salt);
        SecretKeySpec k = new SecretKeySpec(key, "AES");

        c.init(mode, k);
        return c;
    }

    private SecretUtils() {
    }
}
