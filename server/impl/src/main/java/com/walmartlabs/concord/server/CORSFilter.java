package com.walmartlabs.concord.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Singleton
public class CORSFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(CORSFilter.class);

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("CORS filter enabled");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletResponse httpResp = (HttpServletResponse) response;
        httpResp.setHeader("Access-Control-Allow-Origin", "*");
        httpResp.setHeader("Access-Control-Allow-Methods", "*");
        httpResp.setHeader("Access-Control-Allow-Methods", "range");
        httpResp.setHeader("Access-Control-Expose-Headers", "content-range,content-length,accept-ranges");
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }
}
