package com.walmartlabs.concord.server.security.sso.encryption;

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

import com.nimbusds.jose.JWEDecrypter;
import com.nimbusds.jose.KeyLengthException;
import com.nimbusds.jose.crypto.AESDecrypter;
import com.nimbusds.jose.crypto.DirectDecrypter;

import java.util.Arrays;

public class SecretEncryptionConfiguration extends AbstractEncryptionConfiguration {

    private final byte[] secret;

    public SecretEncryptionConfiguration(byte[] secret){
        this.secret = Arrays.copyOf(secret, secret.length);
    }

    @Override
    protected JWEDecrypter buildDecrypter() {
        try {
            if (DirectDecrypter.SUPPORTED_ALGORITHMS.contains(algorithm)) {
                return new DirectDecrypter(this.secret);
            } else {
                return new AESDecrypter(this.secret);
            }
        } catch (KeyLengthException e) {
            throw new RuntimeException(e);
        }
    }
}
