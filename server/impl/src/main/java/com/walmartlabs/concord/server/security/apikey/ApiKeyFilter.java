package com.walmartlabs.concord.server.security.apikey;

import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.web.filter.authc.AuthenticatingFilter;
import org.apache.shiro.web.util.WebUtils;

import javax.inject.Inject;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

public class ApiKeyFilter extends AuthenticatingFilter {

    private final ApiKeyDao apiKeyDao;

    @Inject
    public ApiKeyFilter(ApiKeyDao apiKeyDao) {
        this.apiKeyDao = apiKeyDao;
    }

    @Override
    protected AuthenticationToken createToken(ServletRequest request, ServletResponse response) throws Exception {
        String token = getAuthzHeader(request);
        if (token == null) {
            return new UsernamePasswordToken();
        }

        String userId = apiKeyDao.findUserId(token);
        if (userId == null) {
            return new UsernamePasswordToken();
        }

        return new ApiKey(userId, token);
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

    private static String getAuthzHeader(ServletRequest request) {
        return WebUtils.toHttp(request).getHeader("Authorization");
    }
}
