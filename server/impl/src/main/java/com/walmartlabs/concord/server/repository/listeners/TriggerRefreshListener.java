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

import com.walmartlabs.concord.imports.ImportsListener;
import com.walmartlabs.concord.process.loader.ProjectLoader;
import com.walmartlabs.concord.process.loader.model.ProcessDefinition;
import com.walmartlabs.concord.server.org.project.ProjectValidator;
import com.walmartlabs.concord.server.org.project.RepositoryEntry;
import com.walmartlabs.concord.server.org.triggers.TriggerManager;
import com.walmartlabs.concord.server.process.ImportsNormalizerFactory;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import java.nio.file.Path;

@Named
public class TriggerRefreshListener implements RepositoryRefreshListener {

    private static final Logger log = LoggerFactory.getLogger(TriggerRefreshListener.class);

    private final TriggerManager triggerManager;
    private final ProjectLoader projectLoader;
    private final ImportsNormalizerFactory importsNormalizer;

    @Inject
    public TriggerRefreshListener(TriggerManager triggerManager,
                                  ProjectLoader projectLoader,
                                  ImportsNormalizerFactory importsNormalizer) {

        this.triggerManager = triggerManager;
        this.projectLoader = projectLoader;
        this.importsNormalizer = importsNormalizer;
    }

    @Override
    public void onRefresh(DSLContext ctx, RepositoryEntry repo, Path repoPath) throws Exception {
        if (repo.isTriggersDisabled()) {
            triggerManager.clearTriggers(repo.getProjectId(), repo.getId());
            return;
        }

        log.info("refresh ['{}'] -> triggers", repo.getId());

        ProjectLoader.Result result = projectLoader.loadProject(repoPath, importsNormalizer.forProject(repo.getProjectId()), ImportsListener.NOP_LISTENER);

        ProcessDefinition pd = result.projectDefinition();
        ProjectValidator.Result validationResult = ProjectValidator.validate(pd);
        if (!validationResult.isValid()) {
            throw new ValidationErrorsException(String.join("\n", validationResult.getErrors()));
        }

        triggerManager.refresh(repo.getProjectId(), repo.getId(), pd);
    }
}
