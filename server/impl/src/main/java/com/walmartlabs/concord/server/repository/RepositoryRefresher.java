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
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.repository.Repository;
import com.walmartlabs.concord.server.events.ExternalEventResource;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.project.ProjectAccessManager;
import com.walmartlabs.concord.server.org.project.ProjectEntry;
import com.walmartlabs.concord.server.org.project.RepositoryEntry;
import com.walmartlabs.concord.server.repository.listeners.RepositoryRefreshListener;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import org.jooq.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.walmartlabs.concord.server.org.project.RepositoryUtils.assertRepository;

@Named
public class RepositoryRefresher extends AbstractDao {

    private static final Logger log = LoggerFactory.getLogger(RepositoryRefresher.class);

    private final Set<RepositoryRefreshListener> listeners;
    private final OrganizationManager orgManager;
    private final ProjectAccessManager projectAccessManager;
    private final RepositoryManager repositoryManager;
    private final ExternalEventResource externalEventResource;

    @Inject
    public RepositoryRefresher(@Named("app") Configuration cfg,
                               Set<RepositoryRefreshListener> listeners,
                               OrganizationManager orgManager,
                               ProjectAccessManager projectAccessManager,
                               RepositoryManager repositoryManager,
                               ExternalEventResource externalEventResource) {

        super(cfg);

        this.listeners = listeners;
        this.orgManager = orgManager;
        this.projectAccessManager = projectAccessManager;
        this.repositoryManager = repositoryManager;
        this.externalEventResource = externalEventResource;
    }

    public void refresh(String orgName, String projectName, String repositoryName, boolean sync) {
        UUID orgId = orgManager.assertAccess(orgName, true).getId();
        ProjectEntry projectEntry = assertProject(orgId, projectName, ResourceAccessLevel.READER, true);
        UUID projectId = projectEntry.getId();

        RepositoryEntry repositoryEntry = assertRepository(projectEntry, repositoryName);

        if (!sync) {
            Map<String, Object> event = new HashMap<>();
            event.put("event", "repositoryRefresh");
            event.put("org", orgName);
            event.put("project", projectName);
            event.put("repository", repositoryName);

            externalEventResource.event("concord", event);
            return;
        }

        Path repoPath = repositoryManager.withLock(repositoryEntry.getUrl(), () -> {
            Repository repo = repositoryManager.fetch(projectId, repositoryEntry);
            Path refreshRepoPath = IOUtils.createTempDir("refreshRepo_");
            IOUtils.copy(repo.path(), refreshRepoPath);
            return refreshRepoPath;
        });

        try {
            tx(tx -> {
                for (RepositoryRefreshListener l : listeners) {
                    l.onRefresh(tx, repositoryEntry, repoPath);
                }
            });
        } catch (Exception e) {
            String errorMessage = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            throw new ConcordApplicationException("Error while refreshing repository: " + errorMessage, e);
        } finally {
            cleanUp(repoPath);
        }
    }

    private ProjectEntry assertProject(UUID orgId, String projectName, ResourceAccessLevel accessLevel, boolean orgMembersOnly) {
        if (projectName == null) {
            throw new ValidationErrorsException("Invalid project name");
        }

        return projectAccessManager.assertProjectAccess(orgId, null, projectName, accessLevel, orgMembersOnly);
    }

    private void cleanUp(Path repoPath) {
        try {
            IOUtils.deleteRecursively(repoPath);
        } catch (IOException e) {
            log.warn("cleanUp ['{}'] -> error: {}", repoPath, e.getMessage());
        }
    }
}
