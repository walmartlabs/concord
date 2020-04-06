package com.walmartlabs.concord.server.plugins.iamsso;

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
import com.nimbusds.jwt.*;
import com.walmartlabs.concord.server.plugins.iamsso.encryption.EncryptionConfiguration;
import com.walmartlabs.concord.server.plugins.iamsso.encryption.EncryptionConfigurationFactory;
import com.walmartlabs.concord.server.plugins.iamsso.signature.SignatureConfiguration;
import com.walmartlabs.concord.server.plugins.iamsso.signature.SignatureConfigurationFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Named
public class JwtAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticator.class);

    private final EncryptionConfiguration encryptionConfiguration;
    private final SignatureConfiguration signatureConfiguration;

    @Inject
    public JwtAuthenticator(SsoConfiguration cfg) {
        this.encryptionConfiguration = EncryptionConfigurationFactory.create(cfg.getTokenEncryptionKey());
        this.signatureConfiguration = SignatureConfigurationFactory.create(cfg.getTokenSigningKey());
    }

    /**
     * Check is the JWT valid
     *
     * @param token the JWT
     * @return <code>true</code> if token valid and not expired
     */
    public boolean isTokenValid(String token) {
        return isTokenValid(token, null);
    }

    /**
     * Check is the JWT valid
     *
     * @param token JWT
     * @param  nonce nonce
     * @return <code>true</code> if token valid, correct nonce and not expired
     */
    public boolean isTokenValid(String token, String nonce) {
        try {
            Map<String, Object> claims = validateTokenAndGetClaims(token);
            if (claims == null) {
                return false;
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

    /**
     * Validates the token and returns the corresponding user login.
     *
     * @param token the JWT
     * @return corresponding user login or <code>null</code> if the JWT is invalid
     */
    public String validateTokenAndGetLogin(String token) {
        Map<String, Object> claims = validateTokenAndGetClaims(token);
        if (claims == null) {
            return null;
        }
        return (String) claims.get("loginId");
    }

    private Map<String, Object> validateTokenAndGetClaims(String token) {

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

    private JWT validateToken(String token) throws ParseException, JOSEException {
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

        if (signedJWT != null) {
            if (signatureConfiguration == null) {
                log.error("validateToken ['{}'] -> JWT is signed and no signature configuration", token);
                return null;
            }

            boolean verified = signatureConfiguration.verify(signedJWT);
            if (!verified) {
                return null;
            }
        }

        return jwt;
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
