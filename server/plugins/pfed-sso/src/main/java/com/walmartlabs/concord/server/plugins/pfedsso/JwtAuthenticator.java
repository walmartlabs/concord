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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jwt.*;
import com.walmartlabs.concord.server.plugins.pfedsso.encryption.EncryptionConfiguration;
import com.walmartlabs.concord.server.plugins.pfedsso.encryption.EncryptionConfigurationFactory;
import com.walmartlabs.concord.server.plugins.pfedsso.signature.SignatureConfiguration;
import com.walmartlabs.concord.server.plugins.pfedsso.signature.SignatureConfigurationFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.List;


public class JwtAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticator.class);

    private final EncryptionConfiguration encryptionConfiguration;
    private final SignatureConfiguration signatureConfiguration;
    private final SsoClient ssoClient;
    private final SsoConfiguration cfg;

    private enum ALGOTYPE {
        RSA("RS256"),
        EC("ES256");

        private final String algorithm;

        ALGOTYPE(String algorithm) {
            this.algorithm = algorithm;
        }

        public String getAlgorithm() {
            return this.algorithm;
        }
    }

    @Inject
    public JwtAuthenticator(SsoConfiguration cfg, SsoClient ssoClient) {
        this.encryptionConfiguration = EncryptionConfigurationFactory.create(cfg.getTokenEncryptionKey());
        this.signatureConfiguration = SignatureConfigurationFactory.create(cfg.getTokenSigningKey());
        this.ssoClient = ssoClient;
        this.cfg = cfg;
    }

    /**
     * Check is the JWT valid
     *
     * @param token the JWT
     * @return <code>true</code> if token valid and not expired
     */
    public boolean isTokenValid(String token, boolean restrictOnClientId) {
        return isTokenValid(token, null, restrictOnClientId);
    }

    /**
     * Check is the JWT valid
     *
     * @param token JWT
     * @param nonce nonce
     * @return <code>true</code> if token valid, correct nonce and not expired
     */
    public boolean isTokenValid(String token, String nonce, boolean restrictOnClientId) {
        try {
            Map<String, Object> claims = validateTokenAndGetClaims(token);
            if (claims == null) {
                return false;
            }

            if (restrictOnClientId) {
                List<String> allowedClientIds = cfg.getAllowedClientIds();
                String clientId = (String) claims.get("client_id");
                if(!allowedClientIds.contains(clientId)) {
                    log.warn("isTokenValid ['{}', '{}'] -> clientId not in allowed list for bearer tokens", token, clientId);
                    return false;
                }
            }

            if (nonce == null) {
                return true;
            }

            String claimsNonce = (String) claims.get("nonce");
            if (claimsNonce == null) {
                log.error("isTokenValid ['{}', '{}'] -> claims without nonce", token, nonce);
                return false;
            }

            return nonce.equals(claimsNonce);
        } catch (Exception e) {
            log.error("isTokenValid ['{}', '{}'] -> error", token, nonce, e);
            return false;
        }
    }

    public Map<String, Object> validateTokenAndGetClaims(String token) {
        try {
            JWT jwt = validateToken(token);
            if (jwt == null) {
                return null;
            }

            return createClaims(jwt);
        } catch (Exception e) {
            log.error("validateTokenAndGetClaims ['{}'] -> error", token, e);
            return null;
        }
    }

    private JWT validateToken(String token) throws ParseException, JOSEException, IOException {
        JWT jwt = JWTParser.parse(token);

        if (jwt instanceof PlainJWT) {
            if (signatureConfiguration == null) {
                log.debug("validateToken ['{}'] -> JWT is not signed and no signature configuration", token);
                return jwt;
            } else {
                log.error("validateToken ['{}'] -> non-signed JWT and signature configuration", token);
                return null;
            }
        }

        SignedJWT signedJWT = null;
        if (jwt instanceof SignedJWT) {
            signedJWT = (SignedJWT) jwt;
        }

        if (jwt instanceof EncryptedJWT) {
            if (encryptionConfiguration == null) {
                log.error("validateToken ['{}'] -> JWT is encrypted and no encryption configuration", token);
                return null;
            }

            final EncryptedJWT encryptedJWT = (EncryptedJWT) jwt;

            encryptionConfiguration.decrypt(encryptedJWT);
            signedJWT = encryptedJWT.getPayload().toSignedJWT();
            if (signedJWT != null) {
                jwt = signedJWT;
            }
        }

        if (signedJWT != null && cfg.isTokenSignatureValidation()) {
            if (signatureConfiguration == null) {
                String kid = signedJWT.getHeader().getKeyID();
                String alg = signedJWT.getHeader().getAlgorithm().getName();
                if (kid == null || alg == null) {
                    log.error("validateToken ['{}'] -> JWT is signed and no signature configuration", token);
                    return null;
                }
                return getSigningKeyFromServerAndVerify(signedJWT, kid, alg);
            }

            boolean verified = signatureConfiguration.verify(signedJWT);
            if (!verified) {
                return null;
            }
        }
        return jwt;
    }

    private JWT getSigningKeyFromServerAndVerify(SignedJWT signedJWT, String kid, String alg) throws IOException, JOSEException {
        String tokenSigningKey = getMatchedSigningKey(ssoClient.getTokenSigningKey(), kid, alg);
        if (tokenSigningKey == null) {
            log.error("validateToken ['{}'] -> JWT is signed and no signature configuration found from remote", signedJWT);
            return null;
        }
        SignatureConfiguration signatureConfiguration = SignatureConfigurationFactory.create(tokenSigningKey);
        return signatureConfiguration.verify(signedJWT) ? signedJWT : null;
    }

    private String getMatchedSigningKey(String tokenSigningKeys, String kid, String alg) {
        if (tokenSigningKeys == null)
            return null;
        JsonObject mapping = JsonParser.parseString(tokenSigningKeys).getAsJsonObject();
        JsonElement keysJson = mapping.get("keys");
        JsonArray keysArray = keysJson.getAsJsonArray();
        for (JsonElement jsonElement : keysArray) {
            String algorithm;
            String jsonKid = jsonElement.getAsJsonObject().get("kid").getAsString();
            String algoType = jsonElement.getAsJsonObject().get("kty").getAsString();
            try {
                algorithm = ALGOTYPE.valueOf(algoType).getAlgorithm();
            } catch (IllegalArgumentException e) {
                log.warn("Algorithm for type: {} not found...", algoType);
                continue;
            }
            if (alg.equals(algorithm)
                    && jsonKid.equals(kid)) {
                return jsonElement.toString();
            }
        }
        return null;
    }

    private Map<String, Object> createClaims(JWT jwt) throws ParseException {
        JWTClaimsSet claimSet = jwt.getJWTClaimsSet();

        Date expTime = claimSet.getExpirationTime();
        if (expTime != null) {
            Date now = new Date();
            if (expTime.before(now)) {
                log.debug("createClaims ['{}'] -> JWT expired", jwt);
                return null;
            }
        }

        return new HashMap<>(claimSet.getClaims());
    }
}
