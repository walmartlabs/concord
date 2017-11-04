package com.walmartlabs.concord.server.events;

import com.walmartlabs.concord.server.repository.CachedRepositoryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Date;
import java.util.UUID;

@Named
public class GithubCallbackResourceImpl implements GithubCallbackResource, Resource {

    private static final Logger log = LoggerFactory.getLogger(GithubCallbackResourceImpl.class);

    private final CachedRepositoryManager.RepositoryCacheDao repositoryDao;

    @Inject
    public GithubCallbackResourceImpl(CachedRepositoryManager.RepositoryCacheDao repositoryDao) {
        this.repositoryDao = repositoryDao;
    }

    @Override
    public String push(UUID repoId) {
        if (!repositoryDao.updateLastPushDate(repoId, new Date())) {
            log.warn("push ['{}'] -> repo not found", repoId);
            return "ok";
        }

        return "ok";
    }
}