package com.walmartlabs.concord.server.security;

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

import com.google.common.io.ByteStreams;
import com.walmartlabs.concord.server.cfg.GithubConfiguration;
import com.walmartlabs.concord.server.security.github.GithubKey;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.codec.Hex;
import org.apache.shiro.util.StringUtils;
import org.apache.shiro.web.filter.authc.AuthenticatingFilter;
import org.apache.shiro.web.util.WebUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public class GithubAuthenticatingFilter extends AuthenticatingFilter {

    private static final Logger log = LoggerFactory.getLogger(GithubAuthenticatingFilter.class);

    private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";
    private static final String SIGNATURE_HEADER = "X-Hub-Signature";

    public static final String HOOK_PROJECT_ID = "hookProjectId";
    public static final String HOOK_REPO_TOKEN = "hookRepoToken";

    private final GithubConfiguration cfg;

    @Inject
    public GithubAuthenticatingFilter(GithubConfiguration cfg) {
        this.cfg = cfg;
    }

    @Override
    public void doFilterInternal(ServletRequest request, ServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        HttpServletRequest req = WebUtils.toHttp(request);
        super.doFilterInternal(new CachingRequestWrapper(req), response, filterChain);
    }

    @Override
    protected AuthenticationToken createToken(ServletRequest request, ServletResponse response) throws Exception {
        CachingRequestWrapper req = (CachingRequestWrapper) request;

        // support for hooks restricted to a specific repository
        UUID projectId = getUUID(req, HOOK_PROJECT_ID);
        String repoToken = getString(req, HOOK_REPO_TOKEN);
        if (projectId != null && repoToken != null) {
            return new GithubKey(null, projectId, repoToken);
        }

        String h = req.getHeader(SIGNATURE_HEADER);
        if (!StringUtils.hasText(h)) {
            log.warn("createToken -> authorization header is missing. URI: '{}'", req.getRequestURI());
            return new UsernamePasswordToken();
        }

        String[] algDigest = h.split("=");
        if (algDigest.length != 2) {
            log.warn("createToken -> invalid format of the authorization header. URI: '{}'", req.getRequestURI());
            return new UsernamePasswordToken();
        }

        if (!"sha1".equals(algDigest[0])) {
            log.warn("createToken -> invalid algorithm of the authorization header '{}'. URI: '{}'", algDigest[0], req.getRequestURI());
            return new UsernamePasswordToken();
        }

        final byte[] payload = req.getPayload();
        SecretKeySpec signingKey = new SecretKeySpec(cfg.getSecret().getBytes(), HMAC_SHA1_ALGORITHM);
        try {
            Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
            mac.init(signingKey);
            byte[] digest = mac.doFinal(payload);
            String digestHex = String.valueOf(Hex.encode(digest));

            if (!algDigest[1].equals(digestHex)) {
                log.error("createToken -> invalid auth digest. Expected: '{}', request: '{}'", digestHex, algDigest[1]);
                return new UsernamePasswordToken();
            }

            return new GithubKey(h, projectId, repoToken);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("createToken -> internal error", e);
            throw e;
        }
    }

    @Override
    protected boolean onAccessDenied(ServletRequest request, ServletResponse response) throws Exception {
        boolean loggedId = executeLogin(request, response);

        if (!loggedId) {
            HttpServletResponse resp = WebUtils.toHttp(response);
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }

        return loggedId;
    }

    private static String getString(HttpServletRequest req, String k) {
        String s = req.getParameter(k);
        if (StringUtils.hasText(s)) {
            return s;
        }
        return null;
    }

    private static UUID getUUID(HttpServletRequest req, String k) {
        String s = getString(req, k);
        if (s == null) {
            return null;
        }
        return UUID.fromString(s);
    }

    static class CachingRequestWrapper extends HttpServletRequestWrapper {

        private byte[] payload;

        private CachingRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        private byte[] getPayload() throws IOException {
            if (payload == null) {
                payload = ByteStreams.toByteArray(getRequest().getInputStream());
            }
            return payload;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(getPayload());
            return new ServletInputStream() {

                @Override
                public int read() {
                    return byteArrayInputStream.read();
                }

                @Override
                public boolean isFinished() {
                    return false;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {

                }
            };
        }
    }
}
