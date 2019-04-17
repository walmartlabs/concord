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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.KeyType;
import com.walmartlabs.concord.server.security.sso.JwkHelper;
import net.minidev.json.JSONObject;

import java.security.KeyPair;

import static java.nio.charset.StandardCharsets.UTF_8;

public class EncryptionConfigurationFactory {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static EncryptionConfiguration create(String cfg) {
        if (cfg == null) {
            return null;
        }

        try {
            JSONObject json = objectMapper.readValue(cfg, JSONObject.class);
            KeyType kty = KeyType.parse(json.getAsString("kty"));
            if (KeyType.EC.equals(kty)) {
                KeyPair key = JwkHelper.buildECKeyPairFromJwk(json);
                return new ECEncryptionConfiguration(key);
            } else if(KeyType.RSA.equals(kty)) {
                KeyPair key = JwkHelper.buildRSAKeyPairFromJwk(json);
                return new RSAEncryptionConfiguration(key);
            } else if (KeyType.OCT.equals(kty)) {
                String secret = JwkHelper.buildSecretFromJwk(json);
                return new SecretEncryptionConfiguration(secret.getBytes(UTF_8));
            } else {
                throw new RuntimeException("unknown key type: " + kty);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
}
