package com.walmartlabs.concord.server.repository;

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

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.imports.ImportsListener;
import com.walmartlabs.concord.process.loader.ProjectLoader;
import com.walmartlabs.concord.process.loader.model.ProcessDefinition;
import com.walmartlabs.concord.repository.Repository;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.project.*;
import com.walmartlabs.concord.server.process.ImportsNormalizerFactory;
import com.walmartlabs.concord.server.repository.listeners.RepositoryRefreshListener;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class RepositoryRefresher extends AbstractDao {

    private static final Logger log = LoggerFactory.getLogger(RepositoryRefresher.class);

    private final Set<RepositoryRefreshListener> listeners;
    private final OrganizationManager orgManager;
    private final ProjectAccessManager projectAccessManager;
    private final RepositoryManager repositoryManager;
    private final RepositoryDao repositoryDao;
    private final ProjectDao projectDao;
    private final ProjectLoader projectLoader;
    private final ImportsNormalizerFactory importsNormalizerFactory;

    @Inject
    public RepositoryRefresher(@MainDB Configuration cfg,
                               Set<RepositoryRefreshListener> listeners,
                               OrganizationManager orgManager,
                               ProjectAccessManager projectAccessManager,
                               RepositoryManager repositoryManager,
                               RepositoryDao repositoryDao,
                               ProjectDao projectDao,
                               ProjectLoader projectLoader,
                               ImportsNormalizerFactory importsNormalizerFactory) {

        super(cfg);

        this.listeners = listeners;
        this.orgManager = orgManager;
        this.projectAccessManager = projectAccessManager;
        this.repositoryManager = repositoryManager;
        this.repositoryDao = repositoryDao;
        this.projectDao = projectDao;
        this.projectLoader = projectLoader;
        this.importsNormalizerFactory = importsNormalizerFactory;
    }

    public void refresh(List<UUID> repositoryIds) {
        List<RepositoryEntry> repositories = repositoryIds.stream()
                .map(repositoryDao::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        for (RepositoryEntry r : repositories) {
            try {
                refresh(r);
            } catch (Exception e) {
                log.warn("refresh ['{}'] -> error: {}", r.getId(), e.getMessage());
            }
        }
    }

    private void refresh(RepositoryEntry r) {
        ProjectEntry project = projectDao.get(r.getProjectId());
        if (project == null) {
            log.warn("refresh ['{}'] -> project not found", r.getProjectId());
            return;
        }

        refresh(project.getOrgName(), project.getName(), r.getName());
    }

    public void refresh(String orgName, String projectName, String repositoryName) {
        UUID orgId = orgManager.assertAccess(orgName, true).getId();
        ProjectEntry projectEntry = assertProject(orgId, projectName);
        RepositoryEntry repositoryEntry = repositoryDao.get(projectEntry.getId(), repositoryName);
        ProcessDefinition processDefinition = processDefinition(repositoryEntry.getProjectId(), repositoryEntry);
        tx(tx -> refresh(tx, projectEntry.getId(), repositoryName , processDefinition));
    }

    public void refresh(DSLContext tx, UUID projectId, String repositoryName, ProcessDefinition pd) {
        try {
            RepositoryEntry repositoryEntry = repositoryDao.get(tx, projectId, repositoryName);
            for (RepositoryRefreshListener l : listeners) {
                l.onRefresh(tx, repositoryEntry, pd);
            }
        } catch (Exception e) {
            String errorMessage = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            throw new ConcordApplicationException("Error while refreshing repository: \n" + errorMessage, e);
        }
    }

    private ProjectEntry assertProject(UUID orgId, String projectName) {
        if (projectName == null) {
            throw new ValidationErrorsException("Invalid project name");
        }

        return projectAccessManager.assertAccess(orgId, null, projectName, ResourceAccessLevel.READER, true);
    }

    private ProcessDefinition processDefinition(UUID projectId, RepositoryEntry repositoryEntry) {
        try {
            return repositoryManager.withLock(repositoryEntry.getUrl(), () -> {
                Repository repository = repositoryManager.fetch(projectId, repositoryEntry);
                ProjectLoader.Result result = projectLoader.loadProject(repository.path(), importsNormalizerFactory.forProject(projectId), ImportsListener.NOP_LISTENER);
                return result.projectDefinition();
            });
        } catch (Exception e) {
            throw new ConcordApplicationException("Error while loading process definition: \n" + e.getMessage(), e);
        }
    }
}
