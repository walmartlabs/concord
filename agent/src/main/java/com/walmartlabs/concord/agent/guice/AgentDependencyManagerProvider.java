package com.walmartlabs.concord.agent.guice;

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

import com.walmartlabs.concord.agent.cfg.AgentConfiguration;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.io.IOException;

@Singleton
public class AgentDependencyManagerProvider implements Provider<AgentDependencyManager> {

    private final AgentConfiguration cfg;

    @Inject
    public AgentDependencyManagerProvider(AgentConfiguration cfg) {
        this.cfg = cfg;
    }

    @Override
    public AgentDependencyManager get() {
        try {
            return new AgentDependencyManager(cfg.getDependencyCacheDir());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
