package com.walmartlabs.concord.server.org.project;

import com.walmartlabs.concord.server.api.events.EventResource;
import com.walmartlabs.concord.server.api.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.api.org.project.ProjectEntry;
import com.walmartlabs.concord.server.api.org.project.RepositoryEntry;
import com.walmartlabs.concord.server.api.org.secret.SecretEntry;
import com.walmartlabs.concord.server.events.Events;
import com.walmartlabs.concord.server.events.GithubWebhookService;
import com.walmartlabs.concord.server.org.secret.SecretManager;
import com.walmartlabs.concord.server.security.UserPrincipal;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.Projects.PROJECTS;

@Named
public class ProjectManager {

    private final ProjectDao projectDao;
    private final RepositoryDao repositoryDao;
    private final ProjectAccessManager accessManager;
    private final SecretManager secretManager;
    private final GithubWebhookService githubWebhookService;
    private final EventResource eventResource;

    @Inject
    public ProjectManager(ProjectDao projectDao,
                          RepositoryDao repositoryDao,
                          ProjectAccessManager accessManager,
                          SecretManager secretManager,
                          GithubWebhookService githubWebhookService,
                          EventResource eventResource) {

        this.projectDao = projectDao;
        this.repositoryDao = repositoryDao;
        this.accessManager = accessManager;
        this.secretManager = secretManager;
        this.githubWebhookService = githubWebhookService;
        this.eventResource = eventResource;
    }

    public ProjectEntry get(UUID projectId) {
        return accessManager.assertProjectAccess(projectId, ResourceAccessLevel.READER, false);
    }

    public UUID insert(UUID orgId, String orgName, ProjectEntry entry) {
        Map<String, RepositoryEntry> repos = entry.getRepositories();
        assertSecrets(orgId, repos);

        UserPrincipal p = UserPrincipal.getCurrent();
        UUID ownerId = p.getId();

        return projectDao.txResult(tx -> {
            boolean acceptsRawPayload = entry.getAcceptsRawPayload() != null ? entry.getAcceptsRawPayload() : true;
            UUID pId = projectDao.insert(tx, orgId, entry.getName(), entry.getDescription(), ownerId, entry.getCfg(),
                    entry.getVisibility(), acceptsRawPayload);

            if (repos != null) {
                Map<String, UUID> ids = insertRepos(tx, orgId, orgName, pId, entry.getName(), repos, true);

                repos.forEach((repoName, repo) -> {
                    githubWebhookService.register(pId, ids.get(repoName), repo.getUrl());
                });
            }

            return pId;
        });
    }

    public void update(UUID projectId, ProjectEntry entry) {
        ProjectEntry prevEntry = accessManager.assertProjectAccess(projectId, ResourceAccessLevel.WRITER, true);
        UUID orgId = prevEntry.getOrgId();

        Map<String, RepositoryEntry> repos = entry.getRepositories();
        assertSecrets(orgId, repos);

        projectDao.tx(tx -> {
            githubWebhookService.unregister(projectId);

            projectDao.update(tx, orgId, projectId, entry.getName(), entry.getDescription(), entry.getCfg());

            if (repos != null) {
                repositoryDao.deleteAll(tx, projectId);
                Map<String, UUID> ids = insertRepos(tx, orgId, prevEntry.getOrgName(), projectId, entry.getName(), repos, true);
                repos.forEach((repoName, RepositoryEntry) ->
                        githubWebhookService.register(projectId, ids.get(repoName), RepositoryEntry.getUrl()));
            }
        });
    }

    public void delete(UUID projectId) {
        accessManager.assertProjectAccess(projectId, ResourceAccessLevel.WRITER, true);

        projectDao.tx(tx -> {
            githubWebhookService.unregister(projectId);
            projectDao.delete(tx, projectId);
        });
    }

    public List<ProjectEntry> list(UUID orgId) {
        UserPrincipal p = UserPrincipal.getCurrent();
        UUID userId = p.getId();
        if (p.isAdmin()) {
            // admins can see any project, so we shouldn't filter projects by user
            userId = null;
        }

        return projectDao.list(orgId, userId, PROJECTS.PROJECT_NAME, true);
    }

    private void assertSecrets(UUID orgId, Map<String, RepositoryEntry> repos) {
        if (repos == null) {
            return;
        }

        for (Map.Entry<String, RepositoryEntry> r : repos.entrySet()) {
            String secretName = r.getValue().getSecret();
            if (secretName == null) {
                continue;
            }
            secretManager.assertAccess(orgId, null, secretName, ResourceAccessLevel.READER, false);
        }
    }

    private Map<String, UUID> insertRepos(DSLContext tx, UUID orgId, String orgName,
                                          UUID projectId, String projectName,
                                          Map<String, RepositoryEntry> repos,
                                          boolean update) {

        Map<String, UUID> ids = new HashMap<>();

        for (Map.Entry<String, RepositoryEntry> r : repos.entrySet()) {
            String name = r.getKey();
            RepositoryEntry req = r.getValue();

            UUID secretId = null;
            if (req.getSecret() != null) {
                SecretEntry e = secretManager.assertAccess(orgId, null, req.getSecret(), ResourceAccessLevel.READER, false);
                secretId = e.getId();
            }

            UUID id = repositoryDao.insert(tx, projectId, name, req.getUrl(),
                    trim(req.getBranch()),
                    trim(req.getCommitId()),
                    trim(req.getPath()),
                    secretId);

            eventResource.event(Events.CONCORD_EVENT, update ? Events.Repository.repositoryUpdated(orgName, projectName, req.getName()) :
                    Events.Repository.repositoryCreated(orgName, projectName, req.getName()));

            ids.put(name, id);
        }

        return ids;
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }

        s = s.trim();

        if (s.isEmpty()) {
            return null;
        }

        return s;
    }
}
