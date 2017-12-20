package com.walmartlabs.concord.server.org.secret;

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

import com.walmartlabs.concord.sdk.Secret;

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

    public static byte[] encrypt(byte[] input, byte[] password, byte[] salt) throws GeneralSecurityException {
        Cipher c = init(password, salt, Cipher.ENCRYPT_MODE);
        return c.doFinal(input);
    }

    public static byte[] decrypt(byte[] input, byte[] password, byte[] salt) throws GeneralSecurityException {
        Cipher c = init(password, salt, Cipher.DECRYPT_MODE);
        return c.doFinal(input);
    }

    public static byte[] hash(byte[] in, byte[] salt) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        digest.update(salt);
        return digest.digest(in);
    }

    private static Cipher init(byte[] password, byte[] salt, int mode) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("AES");

        byte[] key = hash(password, salt);
        SecretKeySpec k = new SecretKeySpec(key, "AES");

        c.init(mode, k);
        return c;
    }

    private SecretUtils() {
    }
}
