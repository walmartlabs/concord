package com.walmartlabs.concord.server.boot;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.ErrorHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;

public class CustomErrorHandler extends ErrorHandler {

    private final Set<RequestErrorHandler> handlers;

    public CustomErrorHandler(Set<RequestErrorHandler> handlers) {
        this.handlers = handlers;
    }

    @Override
    public void doError(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
        for (RequestErrorHandler h : handlers) {
            if (h.handle(request, response)) {
                // automatically set the correct Cache-Control headers
                String cacheControl = getCacheControl();
                if (cacheControl != null) {
                    response.setHeader(HttpHeader.CACHE_CONTROL.asString(), cacheControl);
                }
                return;
            }
        }

        super.doError(target, baseRequest, request, response);
    }
}
