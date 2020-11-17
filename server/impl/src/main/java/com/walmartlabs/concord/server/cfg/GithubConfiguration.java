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

import com.walmartlabs.ollie.config.Config;
import org.eclipse.sisu.Nullable;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

@Named
@Singleton
public class GithubConfiguration {

    @Inject
    @Config("github.secret")
    @Nullable
    private String secret;

    @Inject
    @Config("github.useSenderLdapDn")
    private boolean useSenderLdapDn;

    @Inject
    @Config("github.logEvents")
    private boolean logEvents;

    public String getSecret() {
        return secret;
    }

    public boolean isUseSenderLdapDn() {
        return useSenderLdapDn;
    }

    public boolean isLogEvents() {
        return logEvents;
    }
}
