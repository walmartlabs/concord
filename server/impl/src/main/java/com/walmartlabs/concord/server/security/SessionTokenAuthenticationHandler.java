package com.walmartlabs.concord.server.security;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.common.secret.SecretUtils;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.boot.filters.AuthenticationHandler;
import com.walmartlabs.concord.server.cfg.SecretStoreConfiguration;
import com.walmartlabs.concord.server.security.sessionkey.SessionKey;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.subject.support.DefaultSubjectContext;

import javax.inject.Inject;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.HttpHeaders;
import java.util.Base64;
import java.util.UUID;

/**
 * Handles session tokens.
 */
public class SessionTokenAuthenticationHandler implements AuthenticationHandler {

    private static final String BASIC_AUTH_PREFIX = "Basic ";

    private final SecretStoreConfiguration secretCfg;

    @Inject
    public SessionTokenAuthenticationHandler(SecretStoreConfiguration secretCfg) {
        this.secretCfg = secretCfg;
    }

    @Override
    public AuthenticationToken createToken(ServletRequest request, ServletResponse response) {
        var req = (HttpServletRequest) request;

        var encryptedToken = req.getHeader(Constants.Headers.SESSION_TOKEN);
        if (encryptedToken == null) {
            // handle the special case of session tokens passed as usernames
            var auth = req.getHeader(HttpHeaders.AUTHORIZATION);
            if (auth == null) {
                // no session token header and no basic auth header, skip
                return null;
            }

            if (!auth.startsWith(BASIC_AUTH_PREFIX)) {
                return null;
            }

            auth = auth.substring(BASIC_AUTH_PREFIX.length());
            auth = new String(Base64.getDecoder().decode(auth));

            var idx = auth.indexOf(":");
            if (idx < 0 || idx != auth.length() - 1) {
                // invalid auth header or a non-empty password, skip
                return null;
            }

            encryptedToken = auth.substring(0, idx);
        }

        var decryptedValue = decryptSessionKey(encryptedToken);
        var token = new SessionKey(decryptedValue);

        // explicitly disable sessions
        req.setAttribute(DefaultSubjectContext.SESSION_CREATION_ENABLED, false);

        return token;
    }

    private UUID decryptSessionKey(String h) {
        var salt = secretCfg.getSecretStoreSalt();
        var pwd = secretCfg.getServerPwd();
        var ab = SecretUtils.decrypt(Base64.getDecoder().decode(h), pwd, salt);
        return UUID.fromString(new String(ab));
    }
}
