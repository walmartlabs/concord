package com.walmartlabs.concord.server.repository;

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.pipelines.processors.PayloadProcessor;
import org.eclipse.jgit.api.errors.GitAPIException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Adds repository files to a payload.
 */
@Named
public class RepositoryProcessor implements PayloadProcessor {

    private final RepositoryDao repositoryDao;

    @Inject
    public RepositoryProcessor(RepositoryDao repositoryDao) {
        this.repositoryDao = repositoryDao;
    }

    @Override
    public Payload process(Payload payload) {
        String projectId = payload.getHeader(Payload.PROJECT_ID);
        String[] entryPoint = payload.getHeader(Payload.ENTRY_POINT);
        if (projectId == null || entryPoint == null || entryPoint.length < 1) {
            return payload;
        }

        // the name of a repository is always a second part in an entry point, but
        // we extracted project's name earlier
        // TODO remove when the support for default repositories will be implemented
        String repoName = entryPoint[0];

        String url = repositoryDao.findUrl(projectId, repoName);
        if (url == null) {
            return payload;
        }

        try {
            Path src = GitRepository.checkout(url);
            Path dst = payload.getHeader(Payload.WORKSPACE_DIR);
            IOUtils.copy(src, dst);
        } catch (IOException | GitAPIException e) {
            throw new WebApplicationException("Error while pulling a repository: " + url, e);
        }

        // TODO replace with a queue/stack/linkedlist?
        entryPoint = entryPoint.length > 1 ? Arrays.copyOfRange(entryPoint, 1, entryPoint.length) : new String[0];
        return payload.putHeader(Payload.ENTRY_POINT, entryPoint);
    }
}
