package com.walmartlabs.concord.server.repository.listeners;

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

import com.walmartlabs.concord.runtime.loader.ProjectLoader;
import com.walmartlabs.concord.runtime.loader.model.ProcessDefinition;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import com.walmartlabs.concord.server.org.project.RepositoryEntry;
import com.walmartlabs.concord.server.process.ImportsNormalizerFactory;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.nio.file.Path;
import java.util.*;

@Named
public class ProcessDefinitionRefreshListener implements RepositoryRefreshListener {

    private static final Logger log = LoggerFactory.getLogger(ProcessDefinitionRefreshListener.class);

    private final RepositoryDao repositoryDao;
    private final ProjectLoader projectLoader;
    private final ImportsNormalizerFactory importsNormalizer;

    @Inject
    public ProcessDefinitionRefreshListener(RepositoryDao repositoryDao,
                                            ProjectLoader projectLoader,
                                            ImportsNormalizerFactory importsNormalizer) {

        this.repositoryDao = repositoryDao;
        this.projectLoader = projectLoader;
        this.importsNormalizer = importsNormalizer;
    }

    @Override
    public void onRefresh(DSLContext ctx, RepositoryEntry repo, Path repoPath) throws Exception {
        ProcessDefinition pd = projectLoader.loadProject(repoPath, importsNormalizer.forProject(repo.getProjectId()))
                .projectDefinition();

        Set<String> entryPoints = pd.flows().keySet();
        List<String> profiles = new ArrayList<>(pd.profiles().keySet());

        Map<String, Object> meta = new HashMap<>();
        meta.put("entryPoints", emptyToNull(entryPoints));
        meta.put("profiles", emptyToNull(profiles));
        repositoryDao.updateMeta(ctx, repo.getId(), meta);

        log.info("onRefresh ['{}', '{}'] -> done ({})", repo.getId(), repoPath, entryPoints);
    }

    private static <E extends Collection<?>> E emptyToNull(E items) {
        if (items.isEmpty()) {
            return null;
        }
        return items;
    }
}
