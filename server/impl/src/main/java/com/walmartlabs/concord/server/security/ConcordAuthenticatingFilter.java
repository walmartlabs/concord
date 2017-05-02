package com.walmartlabs.concord.server.security;

import com.walmartlabs.concord.server.security.apikey.ApiKey;
import com.walmartlabs.concord.server.security.apikey.ApiKeyDao;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.support.DefaultSubjectContext;
import org.apache.shiro.web.filter.authc.AuthenticatingFilter;
import org.apache.shiro.web.util.WebUtils;

import javax.inject.Inject;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;
import java.util.Base64;

public class ConcordAuthenticatingFilter extends AuthenticatingFilter {

    private static final String AUTHORIZATION_HEADER = HttpHeaders.AUTHORIZATION;
    private static final String BASIC_AUTH_PREFIX = "Basic ";

    /**
     * List of URLs that do not require authentication or authorization.
     */
    private static final String[] ANON_URLS = {
            "/api/v1/server/ping",
            "/api/service/console/logout"};

    private static final String[] FORCE_BASIC_AUTH_URLS = {
            "/forms/.*",
            "/api/service/process_portal/.*"
    };

    private final ApiKeyDao apiKeyDao;

    @Inject
    public ConcordAuthenticatingFilter(ApiKeyDao apiKeyDao) {
        this.apiKeyDao = apiKeyDao;
    }

    @Override
    public boolean onPreHandle(ServletRequest request, ServletResponse response, Object mappedValue) throws Exception {
        HttpServletRequest r = WebUtils.toHttp(request);
        String p = r.getRequestURI();
        for (String s : ANON_URLS) {
            if (p.matches(s)) {
                return true;
            }
        }

        return super.onPreHandle(request, response, mappedValue);
    }

    @Override
    protected AuthenticationToken createToken(ServletRequest request, ServletResponse response) throws Exception {
        String h = getAuthzHeader(request);
        if (h == null) {
            return new UsernamePasswordToken();
        }

        if (h.startsWith(BASIC_AUTH_PREFIX)) {
            // create sessions if users are using username/password auth
            request.setAttribute(DefaultSubjectContext.SESSION_CREATION_ENABLED, Boolean.TRUE);
            return parseBasicAuth(h);
        } else {
            // disable session creation for api token users
            request.setAttribute(DefaultSubjectContext.SESSION_CREATION_ENABLED, Boolean.FALSE);

            String userId = apiKeyDao.findUserId(h);
            if (userId == null) {
                return new UsernamePasswordToken();
            }

            return new ApiKey(userId, h);
        }
    }

    @Override
    protected boolean onAccessDenied(ServletRequest request, ServletResponse response) throws Exception {
        boolean loggedId = executeLogin(request, response);

        if (!loggedId) {
            HttpServletResponse resp = WebUtils.toHttp(response);
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

            forceBasicAuthIfNeeded(request, response);
        }

        return loggedId;
    }

    private static void forceBasicAuthIfNeeded(ServletRequest request, ServletResponse response) {
        // send "WWW-Authenticate: Basic", but only for specific requests w/o
        // authentication header or with basic authentication

        HttpServletRequest req = WebUtils.toHttp(request);
        HttpServletResponse resp = WebUtils.toHttp(response);

        String authHeader = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || authHeader.contains("Basic")) {
            String p = req.getRequestURI();
            for (String s : FORCE_BASIC_AUTH_URLS) {
                if (p.matches(s)) {
                    resp.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic");
                    break;
                }
            }
        }
    }

    private static String getAuthzHeader(ServletRequest request) {
        return WebUtils.toHttp(request).getHeader(AUTHORIZATION_HEADER);
    }

    private static UsernamePasswordToken parseBasicAuth(String s) {
        s = s.substring(BASIC_AUTH_PREFIX.length());
        s = new String(Base64.getDecoder().decode(s));

        String[] as = s.split(":");
        if (as.length != 2) {
            throw new IllegalArgumentException("Invalid basic auth header");
        }

        return new UsernamePasswordToken(as[0], as[1]);
    }
}
