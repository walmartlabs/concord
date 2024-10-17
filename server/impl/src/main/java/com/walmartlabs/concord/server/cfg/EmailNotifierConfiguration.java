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

import javax.inject.Inject;
import java.io.Serializable;
import java.time.Duration;

public class EmailNotifierConfiguration implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    @Config("email.host")
    private String host;

    @Inject
    @Config("email.port")
    private int port;

    @Inject
    @Config("email.enabled")
    private boolean enabled;

    @Inject
    @Config("email.from")
    private String from;

    @Inject
    @Config("email.readTimeout")
    private Duration readTimeout;

    @Inject
    @Config("email.connectTimeout")
    private Duration connectTimeout;

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getFrom() {
        return from;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }
}
