package com.walmartlabs.concord.server.security.sso.signature;

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
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;

import java.util.Arrays;

public class SecretSignatureConfiguration implements SignatureConfiguration {

    private final byte[] secret;

    public SecretSignatureConfiguration(byte[] secret) {
        this.secret = Arrays.copyOf(secret, secret.length);
    }

    @Override
    public boolean verify(final SignedJWT jwt) throws JOSEException {
        final JWSVerifier verifier = new MACVerifier(this.secret);
        return jwt.verify(verifier);
    }
}
