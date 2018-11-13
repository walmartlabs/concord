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

import com.google.common.base.Strings;
import com.walmartlabs.ollie.config.Config;
import org.eclipse.sisu.Nullable;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Map;

@Named
@Singleton
public class GithubConfiguration {

    @Inject
    @Config("github.secret")
    @Nullable
    private String secret;

    @Inject
    @Config("github.githubDomain")
    private String githubDomain;

    @Inject
    @Config("github.defaultFilter")
    private Map<String, Object> defaultFilter;

    public String getSecret() {
        return secret;
    }

    public String getGithubDomain() {
        return githubDomain;
    }

    public Map<String, Object> getDefaultFilter() {
        return defaultFilter;
    }
}
