package com.walmartlabs.concord.server.cfg;

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

import com.walmartlabs.concord.config.Config;
import org.eclipse.jetty.server.CustomRequestLog;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.Serializable;
import java.time.Duration;

public class ServerConfiguration implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String ACCESS_LOG_FORMAT = CustomRequestLog.EXTENDED_NCSA_FORMAT + " %{ms}T";

    @Inject
    @Config("server.port")
    private int port;

    @Inject
    @Config("server.secureCookies")
    private boolean secureCookies;

    @Inject
    @Config("server.cookieComment")
    private String cookieComment;

    @Inject
    @Config("server.sessionTimeout")
    private Duration sessionTimeout;

    @Inject
    @Nullable
    @Config("server.accessLogPath")
    private String accessLogPath;

    @Inject
    @Config("server.accessLogRetainDays")
    private int accessLogRetainDays;

    @Inject
    @Nullable
    @Config("server.baseResourcePath")
    private String baseResourcePath;

    @Inject
    @Config("server.requestHeaderSize")
    private int requestHeaderSize;

    @Inject
    private CORSConfiguration corsConfiguration;

    public int getPort() {
        return port;
    }

    public boolean isSecureCookies() {
        return secureCookies;
    }

    public String getCookieComment() {
        return cookieComment;
    }

    public Duration getSessionTimeout() {
        return sessionTimeout;
    }

    @Nullable
    public String getAccessLogPath() {
        return accessLogPath;
    }

    public int getAccessLogRetainDays() {
        return accessLogRetainDays;
    }

    @Nullable
    public String getBaseResourcePath() {
        return baseResourcePath;
    }

    public int getRequestHeaderSize() {
        return requestHeaderSize;
    }

    public CORSConfiguration getCORSConfiguration() {
        return corsConfiguration;
    }

    public static class CORSConfiguration implements Serializable {

        private static final long serialVersionUID = 1L;

        @Inject
        @Config("server.cors.allowOrigin")
        private String allowOrigin;

        public String getAllowOrigin() {
            return allowOrigin;
        }
    }
}
