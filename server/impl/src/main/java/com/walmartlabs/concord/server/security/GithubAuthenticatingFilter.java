package com.walmartlabs.concord.server.security;

import com.google.common.io.ByteStreams;
import com.walmartlabs.concord.server.cfg.GithubConfiguration;
import org.apache.shiro.codec.Hex;
import org.apache.shiro.util.StringUtils;
import org.apache.shiro.web.servlet.OncePerRequestFilter;
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

public class GithubAuthenticatingFilter extends OncePerRequestFilter {

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
        HttpServletResponse resp = WebUtils.toHttp(response);

        String h = req.getHeader(SIGNATURE_HEADER);
        if (!StringUtils.hasText(h)) {
            log.warn("Authorization header is missing. URI: '{}'", req.getRequestURI());
            resp.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String[] algDigest = h.split("=");
        if(algDigest.length != 2) {
            log.warn("Authorization header invalid format. URI: '{}'", req.getRequestURI());
            resp.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        if(!"sha1".equals(algDigest[0])) {
            log.warn("Authorization header invalid algorithm '{}'. URI: '{}'", algDigest[0], req.getRequestURI());
            resp.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        CachingRequestWrapper requestWrapper = new CachingRequestWrapper(req);

        final byte[] payload = requestWrapper.getContentAsByteArray();
        SecretKeySpec signingKey = new SecretKeySpec(githubCfg.getSecret().getBytes(), HMAC_SHA1_ALGORITHM);
        try {
            Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
            mac.init(signingKey);
            byte[] digest = mac.doFinal(payload);
            String digestHex = String.valueOf(Hex.encode(digest));

            if(!algDigest[1].equals(digestHex)) {
                log.error("Invalid auth digest. Expected: '{}', request: '{}'", digestHex, algDigest[1]);
                resp.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Internal error", e);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        filterChain.doFilter(requestWrapper, response);
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
