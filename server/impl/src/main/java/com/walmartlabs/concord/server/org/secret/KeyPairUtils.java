package com.walmartlabs.concord.server.org.secret;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.walmartlabs.concord.common.secret.KeyPair;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public final class KeyPairUtils {

    private static final int DEFAULT_KEY_TYPE = com.jcraft.jsch.KeyPair.RSA;
    private static final String DEFAULT_KEY_COMMENT = "concord-server";

    private static final JSch jsch = new JSch();
    private static final Lock mutex = new ReentrantLock();

    public static KeyPair create(int keySize) {
        com.jcraft.jsch.KeyPair k;

        mutex.lock();
        try {
            k = com.jcraft.jsch.KeyPair.genKeyPair(jsch, DEFAULT_KEY_TYPE, keySize);
        } catch (JSchException e) {
            throw new SecurityException(e);
        } finally {
            mutex.unlock();
        }

        byte[] publicKey = array(out -> k.writePublicKey(out, DEFAULT_KEY_COMMENT));
        byte[] privateKey = array(k::writePrivateKey);

        return new KeyPair(publicKey, privateKey);
    }

    public static KeyPair create(InputStream publicIn, InputStream privateIn) throws IOException {
        byte[] publicKey = publicIn.readAllBytes();
        byte[] privateKey = privateIn.readAllBytes();
        return new KeyPair(publicKey, privateKey);
    }

    private static byte[] array(Consumer<OutputStream> c) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        c.accept(out);
        return out.toByteArray();
    }

    private KeyPairUtils() {
    }
}
