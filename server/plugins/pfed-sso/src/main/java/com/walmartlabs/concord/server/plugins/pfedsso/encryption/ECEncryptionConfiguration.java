package com.walmartlabs.concord.server.plugins.pfedsso.encryption;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEDecrypter;
import com.nimbusds.jose.crypto.ECDHDecrypter;

import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;

public class ECEncryptionConfiguration extends AbstractEncryptionConfiguration {

    private final ECPublicKey publicKey;

    private final ECPrivateKey privateKey;

    public ECEncryptionConfiguration(final KeyPair keyPair) {
        this.privateKey = (ECPrivateKey) keyPair.getPrivate();
        this.publicKey = (ECPublicKey) keyPair.getPublic();
    }

    @Override
    protected JWEDecrypter buildDecrypter() {
        try {
            return new ECDHDecrypter(this.privateKey);
        } catch (final JOSEException e) {
            throw new RuntimeException(e);
        }
    }

    public ECPublicKey getPublicKey() {
        return publicKey;
    }
}
