package com.walmartlabs.concord.server.repository;

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

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.common.TemporaryPath;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.imports.ImportsListener;
import com.walmartlabs.concord.process.loader.ImportsNormalizer;
import com.walmartlabs.concord.process.loader.ProjectLoader;
import com.walmartlabs.concord.process.loader.model.ProcessDefinition;
import com.walmartlabs.concord.repository.Repository;
import com.walmartlabs.concord.server.events.ExternalEventResource;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.project.*;
import com.walmartlabs.concord.server.process.ImportsNormalizerFactory;
import com.walmartlabs.concord.server.repository.listeners.RepositoryRefreshListener;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import org.jooq.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Named
public class RepositoryRefresher extends AbstractDao {

    private static final Logger log = LoggerFactory.getLogger(RepositoryRefresher.class);

    private final Set<RepositoryRefreshListener> listeners;
    private final OrganizationManager orgManager;
    private final ProjectAccessManager projectAccessManager;
    private final RepositoryManager repositoryManager;
    private final ExternalEventResource externalEventResource;
    private final RepositoryDao repositoryDao;
    private final ProjectDao projectDao;
    private final ProjectLoader projectLoader;
    private final ImportsNormalizerFactory importsNormalizerFactory;

    private final ProjectRepositoryManager projectRepositoryManager;

    @Inject
    public RepositoryRefresher(@MainDB Configuration cfg,
                               Set<RepositoryRefreshListener> listeners,
                               OrganizationManager orgManager,
                               ProjectAccessManager projectAccessManager,
                               RepositoryManager repositoryManager,
                               ExternalEventResource externalEventResource,
                               RepositoryDao repositoryDao,
                               ProjectDao projectDao,
                               ProjectRepositoryManager projectRepositoryManager,
                               ProjectLoader projectLoader,
                               ImportsNormalizerFactory importsNormalizerFactory) {

        super(cfg);

        this.listeners = listeners;
        this.orgManager = orgManager;
        this.projectAccessManager = projectAccessManager;
        this.repositoryManager = repositoryManager;
        this.externalEventResource = externalEventResource;
        this.repositoryDao = repositoryDao;
        this.projectDao = projectDao;
        this.projectRepositoryManager = projectRepositoryManager;
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
        refresh(project.getOrgName(), project.getName(), r.getName(), true);
    }

    public void refresh(String orgName, String projectName, String repositoryName, boolean sync) {
        UUID orgId = orgManager.assertAccess(orgName, true).getId();
        ProjectEntry projectEntry = assertProject(orgId, projectName);
        RepositoryEntry repositoryEntry = projectRepositoryManager.get(projectEntry.getId(), repositoryName);

        if (!sync) {
            Map<String, Object> event = new HashMap<>();
            event.put("event", "repositoryRefresh");
            event.put("org", orgName);
            event.put("project", projectName);
            event.put("repository", repositoryName);

            externalEventResource.event("concord", event);
            return;
        }

        try (TemporaryPath tmpRepoPath = IOUtils.tempDir("refreshRepo_")) {
            Path path = tmpRepoPath.path();
            ImportsNormalizer normalizer = importsNormalizerFactory.forProject(repositoryEntry.getProjectId());

            repositoryManager.withLock(repositoryEntry.getUrl(), () -> {
                Repository repo = repositoryManager.fetch(projectEntry.getId(), repositoryEntry);
                repo.export(path);
                return null;
            });

            ProcessDefinition pd = projectLoader.loadProject(path, normalizer, ImportsListener.NOP_LISTENER)
                    .projectDefinition();

            tx(tx -> {
                for (RepositoryRefreshListener l : listeners) {
                    l.onRefresh(tx, repositoryEntry, path, pd);
                }
            });
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
}
