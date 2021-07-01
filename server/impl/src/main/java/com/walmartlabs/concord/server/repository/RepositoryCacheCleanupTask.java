package com.walmartlabs.concord.server.repository;

import com.walmartlabs.concord.server.PeriodicTask;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.concurrent.TimeUnit;

@Named
@Singleton
public class RepositoryCacheCleanupTask extends PeriodicTask {

    private static final long ERROR_DELAY = TimeUnit.SECONDS.toMillis(30);

    private final RepositoryManager repositoryManager;

    @Inject
    public RepositoryCacheCleanupTask(RepositoryManager repositoryManager) {
        super(repositoryManager.cleanupInterval(), ERROR_DELAY);
        this.repositoryManager = repositoryManager;
    }

    @Override
    protected boolean performTask() throws Exception {
        repositoryManager.cleanup();
        return false;
    }
}
