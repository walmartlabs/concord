package com.walmartlabs.concord.server.project;

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.api.project.RepositoryEntry;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.keys.HeaderKey;
import com.walmartlabs.concord.server.process.pipelines.processors.PayloadProcessor;
import com.walmartlabs.concord.server.security.secret.Secret;
import com.walmartlabs.concord.server.security.secret.SecretManager;
import org.eclipse.jgit.api.errors.GitAPIException;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Adds repository files to a payload.
 */
@Named
public class RepositoryProcessor implements PayloadProcessor {

    /**
     * Repository effective parameters.
     */
    public static final HeaderKey<RepositoryInfo> REPOSITORY_INFO_KEY = HeaderKey.register("_repositoryInfo", RepositoryInfo.class);

    private static final String DEFAULT_BRANCH = "master";

    private final RepositoryDao repositoryDao;
    private final SecretManager secretManager;

    @Inject
    public RepositoryProcessor(RepositoryDao repositoryDao, SecretManager secretManager) {
        this.repositoryDao = repositoryDao;
        this.secretManager = secretManager;
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
        // TODO remove when the support for default repositories will be implemented?
        String repoName = entryPoint[0];

        RepositoryEntry repo = repositoryDao.getByNameInProject(projectId, repoName);
        if (repo == null) {
            return payload;
        }

        String branch = repo.getBranch();
        if (branch == null || branch.trim().isEmpty()) {
            branch = DEFAULT_BRANCH;
        }

        Secret secret = null;
        if (repo.getSecret() != null) {
            secret = secretManager.getSecret(repo.getSecret().getName());
        }

        try {
            Path src = GitRepository.checkout(repo.getUrl(), branch, secret);
            Path dst = payload.getHeader(Payload.WORKSPACE_DIR);
            IOUtils.copy(src, dst);
        } catch (IOException | GitAPIException e) {
            throw new ProcessException("Error while pulling a repository: " + repo.getUrl(), e);
        }

        // TODO replace with a queue/stack/linkedlist?
        entryPoint = entryPoint.length > 1 ? Arrays.copyOfRange(entryPoint, 1, entryPoint.length) : new String[0];

        return payload.putHeader(Payload.ENTRY_POINT, entryPoint)
                .putHeader(REPOSITORY_INFO_KEY, new RepositoryInfo(repo.getName(), repo.getUrl(), branch));
    }

    public static final class RepositoryInfo implements Serializable {

        private final String name;
        private final String url;
        private final String branch;

        public RepositoryInfo(String name, String url, String branch) {
            this.name = name;
            this.url = url;
            this.branch = branch;
        }

        public String getName() {
            return name;
        }

        public String getUrl() {
            return url;
        }

        public String getBranch() {
            return branch;
        }

        @Override
        public String toString() {
            return "RepositoryInfo{" +
                    "name='" + name + '\'' +
                    ", url='" + url + '\'' +
                    ", branch='" + branch + '\'' +
                    '}';
        }
    }
}
