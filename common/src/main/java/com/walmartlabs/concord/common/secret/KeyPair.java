package com.walmartlabs.concord.common.secret;

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

import com.walmartlabs.concord.sdk.Secret;

import java.io.*;

public class KeyPair implements Secret {

    private static final long serialVersionUID = 1L;

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

    public KeyPair(byte[] publicKey, byte[] privateKey) { // NOSONAR
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
