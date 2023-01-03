package com.walmartlabs.concord.server.plugins.pfedsso.signature;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.SignedJWT;

import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;

public class ECSignatureConfiguration implements SignatureConfiguration {

    private final ECPublicKey publicKey;

    private final ECPrivateKey privateKey;

    public ECSignatureConfiguration(KeyPair keyPair) {
        this.privateKey = (ECPrivateKey) keyPair.getPrivate();
        this.publicKey = (ECPublicKey) keyPair.getPublic();
    }

    @Override
    public boolean verify(final SignedJWT jwt) throws JOSEException {
        final JWSVerifier verifier = new ECDSAVerifier(this.publicKey);
        return jwt.verify(verifier);
    }
}
