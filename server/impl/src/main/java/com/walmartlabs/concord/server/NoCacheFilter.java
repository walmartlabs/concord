package com.walmartlabs.concord.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;
import java.io.IOException;

@Singleton
public class NoCacheFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(NoCacheFilter.class);

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("NoCache filter enabled");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletResponse httpResp = (HttpServletResponse) response;
        httpResp.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
        httpResp.setHeader("Pragma", "no-cache");
        httpResp.setHeader(HttpHeaders.EXPIRES, "0");
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }
}
