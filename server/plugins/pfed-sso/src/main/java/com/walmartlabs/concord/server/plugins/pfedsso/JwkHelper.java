package com.walmartlabs.concord.server.plugins.pfedsso;

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
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.RSAKey;
import net.minidev.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.text.ParseException;

public final class JwkHelper {

    /**
     * Build the secret from the JWK JSON.
     *
     * @param json json
     * @return secret
     */
    public static String buildSecretFromJwk(JSONObject json) {
        try {
            OctetSequenceKey octetSequenceKey = OctetSequenceKey.parse(json);
            return new String(octetSequenceKey.toByteArray(), StandardCharsets.UTF_8);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Build the RSA key pair from the JWK JSON.
     *
     * @param json json
     * @return key pair
     */
    public static KeyPair buildRSAKeyPairFromJwk(JSONObject json) {
        try {
            RSAKey rsaKey = RSAKey.parse(json);
            return rsaKey.toKeyPair();
        } catch (JOSEException | ParseException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Build the EC key pair from the JWK JSON.
     *
     * @param json json
     * @return key pair
     */
    public static KeyPair buildECKeyPairFromJwk(JSONObject json) {
        try {
            ECKey ecKey = ECKey.parse(json);
            return ecKey.toKeyPair();
        } catch (JOSEException | ParseException e) {
            throw new RuntimeException(e);
        }
    }
}
