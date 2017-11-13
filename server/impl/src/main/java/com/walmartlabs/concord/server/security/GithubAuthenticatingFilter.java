package com.walmartlabs.concord.server.security;

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

public class GithubAuthenticatingFilter extends AuthenticatingFilter {

    private static final Logger log = LoggerFactory.getLogger(GithubAuthenticatingFilter.class);

    private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";
    private static final String SIGNATURE_HEADER = "X-Hub-Signature";

    private final GithubConfiguration githubCfg;

    @Inject
    public GithubAuthenticatingFilter(GithubConfiguration githubCfg) {
        this.githubCfg = githubCfg;
    }

    @Override
    public void doFilterInternal(ServletRequest request, ServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        HttpServletRequest req = WebUtils.toHttp(request);
        super.doFilterInternal(new CachingRequestWrapper(req), response, filterChain);
    }

    @Override
    protected AuthenticationToken createToken(ServletRequest request, ServletResponse response) throws Exception {
        CachingRequestWrapper req = (CachingRequestWrapper)request;

        String h = req.getHeader(SIGNATURE_HEADER);
        if (!StringUtils.hasText(h)) {
            log.warn("Authorization header is missing. URI: '{}'", req.getRequestURI());
            return new UsernamePasswordToken();
        }

        String[] algDigest = h.split("=");
        if(algDigest.length != 2) {
            log.warn("Authorization header invalid format. URI: '{}'", req.getRequestURI());
            return new UsernamePasswordToken();
        }

        if(!"sha1".equals(algDigest[0])) {
            log.warn("Authorization header invalid algorithm '{}'. URI: '{}'", algDigest[0], req.getRequestURI());
            return new UsernamePasswordToken();
        }

        final byte[] payload = req.getContentAsByteArray();
        SecretKeySpec signingKey = new SecretKeySpec(githubCfg.getSecret().getBytes(), HMAC_SHA1_ALGORITHM);
        try {
            Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
            mac.init(signingKey);
            byte[] digest = mac.doFinal(payload);
            String digestHex = String.valueOf(Hex.encode(digest));

            if(!algDigest[1].equals(digestHex)) {
                log.error("Invalid auth digest. Expected: '{}', request: '{}'", digestHex, algDigest[1]);
                return new UsernamePasswordToken();
            }

            return new GithubKey(h);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Internal error", e);
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

    static class CachingRequestWrapper extends HttpServletRequestWrapper {

        private byte[] payload;

        public CachingRequestWrapper(HttpServletRequest request) throws IOException {
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
                public int read() throws IOException {
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

        public byte[] getContentAsByteArray() throws IOException {
            return getPayload();
        }
    }
}
