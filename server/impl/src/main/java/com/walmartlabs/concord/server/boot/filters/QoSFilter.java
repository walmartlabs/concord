package com.walmartlabs.concord.server.boot.filters;

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

import com.walmartlabs.concord.server.cfg.QosConfiguration;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * copy from {@link org.eclipse.jetty.servlets.QoSFilter} but with custom error code
 */
@WebFilter(value = {"/api/v1/process/*", "/api/v1/org/*"})
public class QoSFilter implements Filter {

    private static final int TOO_MANY_REQUESTS_CODE = 429;

    private static final int DEFAULT_MAX_PRIORITY = 2;

    // currently we only care about `POST /api/v1/process`
    // and `GET /api/v1/org/{orgName}/project/{projectName}/repo/{repoName}/start/{entryPoint}`
    // requests (i.e. process start requests)
    private static UrlPattern[] PATTERNS = {
            UrlPattern.prefix("/api/v1/process", "POST"),
            UrlPattern.regexp("^/api/v1/org/[^/]*/project/[^/]*/repo/[^/]*/start/[^/]+$", "GET")
    };

    private final String _suspended = "QoSFilter@" + Integer.toHexString(hashCode()) + ".SUSPENDED";
    private final String _resumed = "QoSFilter@" + Integer.toHexString(hashCode()) + ".RESUMED";

    private final long waitMs;
    private final long suspendMs;
    private final int maxRequests;

    private final Queue<AsyncContext>[] queues;
    private final AsyncListener[] listeners;
    private final Semaphore passes;

    @Inject
    public QoSFilter(QosConfiguration qosConfiguration) {
        this.maxRequests = qosConfiguration.getMaxRequests();
        this.waitMs = qosConfiguration.getMaxWait().toMillis();
        this.suspendMs = qosConfiguration.getSuspend().toMillis();

        this.queues = new AsyncContextQueue[DEFAULT_MAX_PRIORITY + 1];
        this.listeners = new AsyncListener[this.queues.length];
        this.passes = new Semaphore(this.maxRequests, true);
    }

    @Override
    public void init(FilterConfig filterConfig) {
        if (isDisabled()) {
            return;
        }

        for (int p = 0; p < this.queues.length; ++p) {
            this.queues[p] = new AsyncContextQueue();
            this.listeners[p] = new QoSAsyncListener(p);
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (isDisabled()) {
            chain.doFilter(request, response);
            return;
        }

        if (needProcessRequest(request)) {
            filter(request, response, chain);
        } else {
            chain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() {
        // do nothing
    }

    private boolean needProcessRequest(ServletRequest request) {
        HttpServletRequest req = (HttpServletRequest) request;

        String uri = req.getRequestURI();
        if (uri == null) {
            return false;
        }

        String method = req.getMethod();
        for (UrlPattern p : PATTERNS) {
            if (p.method().equalsIgnoreCase(method)) {
                if (p.prefix() != null && uri.startsWith(p.prefix())) {
                    return true;
                }
                if (p.regexp() != null && p.regexp().matcher(uri).matches()) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isDisabled() {
        return maxRequests < 0;
    }

    private void filter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        boolean accepted = false;
        try {
            Boolean suspended = (Boolean) request.getAttribute(_suspended);
            if (suspended == null) {
                accepted = passes.tryAcquire(waitMs, TimeUnit.MILLISECONDS);
                if (accepted) {
                    request.setAttribute(_suspended, Boolean.FALSE);
                } else {
                    request.setAttribute(_suspended, Boolean.TRUE);
                    int priority = getPriority(request);
                    AsyncContext asyncContext = request.startAsync();
                    if (suspendMs > 0) {
                        asyncContext.setTimeout(suspendMs);
                    }
                    asyncContext.addListener(listeners[priority]);
                    queues[priority].add(asyncContext);
                    return;
                }
            } else {
                if (suspended) {
                    request.setAttribute(_suspended, Boolean.FALSE);
                    Boolean resumed = (Boolean) request.getAttribute(_resumed);
                    if (Boolean.TRUE.equals(resumed)) {
                        passes.acquire();
                        accepted = true;
                    } else {
                        // Timeout! try 1 more time.
                        accepted = passes.tryAcquire(waitMs, TimeUnit.MILLISECONDS);
                    }
                } else {
                    // Pass through resume of previously accepted request.
                    passes.acquire();
                    accepted = true;
                }
            }

            if (accepted) {
                chain.doFilter(request, response);
            } else {
                ((HttpServletResponse) response).sendError(TOO_MANY_REQUESTS_CODE);
            }
        } catch (InterruptedException e) {
            ((HttpServletResponse) response).sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            Thread.currentThread().interrupt();
        } finally {
            if (accepted) {
                for (int p = queues.length - 1; p >= 0; --p) {
                    AsyncContext asyncContext = queues[p].poll();
                    if (asyncContext != null) {
                        ServletRequest candidate = asyncContext.getRequest();
                        Boolean suspended = (Boolean) candidate.getAttribute(_suspended);
                        if (Boolean.TRUE.equals(suspended)) {
                            candidate.setAttribute(_resumed, Boolean.TRUE);
                            asyncContext.dispatch();
                            break;
                        }
                    }
                }
                passes.release();
            }
        }
    }

    private int getPriority(ServletRequest request) {
        HttpServletRequest baseRequest = (HttpServletRequest) request;
        if (baseRequest.getUserPrincipal() != null) {
            return 2;
        } else {
            HttpSession session = baseRequest.getSession(false);
            if (session != null && !session.isNew())
                return 1;
            else
                return 0;
        }
    }

    private static class AsyncContextQueue extends ConcurrentLinkedQueue<AsyncContext> {
        private static final long serialVersionUID = 1L;
    }

    private class QoSAsyncListener implements AsyncListener {

        private final int priority;

        QoSAsyncListener(int priority) {
            this.priority = priority;
        }

        @Override
        public void onStartAsync(AsyncEvent event) {
        }

        @Override
        public void onComplete(AsyncEvent event) {
        }

        @Override
        public void onTimeout(AsyncEvent event) {
            // Remove before it's redispatched, so it won't be
            // redispatched again at the end of the filtering.
            AsyncContext asyncContext = event.getAsyncContext();
            queues[priority].remove(asyncContext);
            asyncContext.dispatch();
        }

        @Override
        public void onError(AsyncEvent event) {
        }
    }

    @Value.Immutable
    interface UrlPattern {

        @Nullable
        Pattern regexp();

        @Nullable
        String prefix();

        String method();

        static UrlPattern prefix(String prefix, String method) {
            return ImmutableUrlPattern.builder()
                    .prefix(prefix)
                    .method(method)
                    .build();
        }

        static UrlPattern regexp(String regexp, String method) {
            return ImmutableUrlPattern.builder()
                    .regexp(Pattern.compile(regexp))
                    .method(method)
                    .build();
        }
    }
}
