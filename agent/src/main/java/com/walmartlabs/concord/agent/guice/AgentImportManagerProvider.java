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

import com.walmartlabs.concord.agent.RepositoryManager;
import com.walmartlabs.concord.agent.cfg.ImportConfiguration;
import com.walmartlabs.concord.imports.ImportManagerFactory;
import com.walmartlabs.concord.imports.RepositoryExporter;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.nio.file.Path;
import java.util.Objects;

@Singleton
public class AgentImportManagerProvider implements Provider<AgentImportManager> {

    private final ImportManagerFactory factory;

    @Inject
    public AgentImportManagerProvider(ImportConfiguration cfg, RepositoryManager repositoryManager, AgentDependencyManager dependencyManager) {
        RepositoryExporter exporter = (entry, workDir) -> {
            Path dst = workDir;

            String entryDest = entry.dest();
            if (entry.dest() != null) {
                dst = dst.resolve(Objects.requireNonNull(entryDest));
            }

            repositoryManager.export(entry.url(), entry.version(), null, entry.path(), dst, entry.secret(), entry.exclude());
            return null;
        };

        this.factory = new ImportManagerFactory(dependencyManager, exporter, cfg.getDisabledProcessors());
    }

    @Override
    public AgentImportManager get() {
        return new AgentImportManager(factory.create());
    }
}
