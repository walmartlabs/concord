package com.walmartlabs.concord.server.events;

import com.walmartlabs.concord.common.secret.Secret;
import com.walmartlabs.concord.server.api.project.RepositoryEntry;
import com.walmartlabs.concord.server.project.RepositoryDao;
import com.walmartlabs.concord.server.project.RepositoryManager;
import com.walmartlabs.concord.server.security.secret.SecretManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.UUID;

@Named
public class GithubCallbackResourceImpl implements GithubCallbackResource, Resource {

    private static final Logger log = LoggerFactory.getLogger(GithubCallbackResourceImpl.class);

    private final RepositoryDao repositoryDao;

    private final SecretManager secretManager;

    private final RepositoryManager repositoryManager;

    @Inject
    public GithubCallbackResourceImpl(RepositoryDao repositoryDao,
                                      SecretManager secretManager,
                                      RepositoryManager repositoryManager) {
        this.repositoryDao = repositoryDao;
        this.secretManager = secretManager;
        this.repositoryManager = repositoryManager;
    }

    @Override
    public String push(UUID projectId, UUID repoId) {
        RepositoryEntry repo = repositoryDao.get(projectId, repoId);
        if (repo == null) {
            log.warn("push ['{}', '{}'] -> not found", projectId, repoId);
            return "ok";
        }

        Secret secret = null;
        if (repo.getSecret() != null) {
            secret = secretManager.getSecret(repo.getSecret(), null);
            if (secret == null) {
                log.warn("push ['{}', '{}'] -> secret not found", projectId, repoId);
                return "ok";
            }
        }

        if(repo.getCommitId() != null) {
            repositoryManager.fetchByCommit(projectId, repo.getName(), repo.getUrl(), repo.getCommitId(), repo.getPath(), secret);
        } else {
            repositoryManager.fetch(projectId, repo.getName(), repo.getUrl(), repo.getBranch(), repo.getPath(), secret);
        }

        log.info("push ['{}', '{}'] -> ok", projectId, repoId);

        return "ok";
    }
}