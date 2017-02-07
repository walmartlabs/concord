package com.walmartlabs.concord.server.security.secret;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class CryptoUtils {

    public static byte[] encrypt(byte[] input, byte[] password, byte[] salt) throws GeneralSecurityException {
        Cipher c = init(password, salt, Cipher.ENCRYPT_MODE);
        return c.doFinal(input);
    }

    public static byte[] decrypt(byte[] input, byte[] password, byte[] salt) throws GeneralSecurityException {
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

    private CryptoUtils() {
    }
}
