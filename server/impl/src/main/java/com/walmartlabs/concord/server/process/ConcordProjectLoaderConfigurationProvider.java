package com.walmartlabs.concord.server.process;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import com.walmartlabs.concord.process.loader.ConcordProjectLoader;
import com.walmartlabs.concord.server.cfg.ProcessConfiguration;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Optional;
import java.util.Set;

public class ConcordProjectLoaderConfigurationProvider implements Provider<ConcordProjectLoader.Configuration> {

    private final Set<String> extraRuntimes;

    @Inject
    public ConcordProjectLoaderConfigurationProvider(ProcessConfiguration cfg) {
        this.extraRuntimes = Optional.ofNullable(cfg.getExtraRuntimes()).map(Set::copyOf).orElse(Set.of());
    }

    @Override
    public ConcordProjectLoader.Configuration get() {
        return new ConcordProjectLoader.Configuration(Set.copyOf(extraRuntimes));
    }
}
