package com.walmartlabs.concord.server.repository.listeners;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.runtime.model.ProcessDefinition;
import com.walmartlabs.concord.server.org.project.ProjectValidator;
import com.walmartlabs.concord.server.org.project.RepositoryEntry;
import com.walmartlabs.concord.server.org.triggers.TriggerManager;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

public class TriggerRefreshListener implements RepositoryRefreshListener {

    private static final Logger log = LoggerFactory.getLogger(TriggerRefreshListener.class);

    private final TriggerManager triggerManager;

    @Inject
    public TriggerRefreshListener(TriggerManager triggerManager) {
        this.triggerManager = triggerManager;
    }

    @Override
    public void onRefresh(DSLContext tx, RepositoryEntry repo, ProcessDefinition pd) {
        if (repo.isTriggersDisabled() || repo.isDisabled()) {
            triggerManager.clearTriggers(tx, repo.getProjectId(), repo.getId());
            return;
        }

        log.info("refresh ['{}'] -> triggers", repo.getId());

        ProjectValidator.Result validationResult = ProjectValidator.validate(pd);
        if (!validationResult.isValid()) {
            throw new ValidationErrorsException(String.join("\n", validationResult.getErrors()));
        }

        triggerManager.refresh(tx, repo.getProjectId(), repo.getId(), pd);
    }
}
