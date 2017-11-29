package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.api.org.project.RepositoryEntry;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.keys.HeaderKey;
import com.walmartlabs.concord.server.process.logs.LogManager;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import com.walmartlabs.concord.server.org.project.RepositoryException;
import com.walmartlabs.concord.server.repository.RepositoryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

import static com.walmartlabs.concord.server.repository.RepositoryManager.DEFAULT_BRANCH;

/**
 * Adds repository files to a payload.
 */
@Named
public class RepositoryProcessor implements PayloadProcessor {

    private static final Logger log = LoggerFactory.getLogger(RepositoryProcessor.class);

    /**
     * Repository effective parameters.
     */
    public static final HeaderKey<RepositoryInfo> REPOSITORY_INFO_KEY = HeaderKey.register("_repositoryInfo", RepositoryInfo.class);

    private final RepositoryDao repositoryDao;
    private final RepositoryManager repositoryManager;
    private final LogManager logManager;

    @Inject
    public RepositoryProcessor(RepositoryDao repositoryDao,
                               RepositoryManager repositoryManager,
                               LogManager logManager) {

        this.repositoryDao = repositoryDao;
        this.repositoryManager = repositoryManager;
        this.logManager = logManager;
    }

    @Override
    @WithTimer
    public Payload process(Chain chain, Payload payload) {
        UUID instanceId = payload.getInstanceId();

        UUID projectId = payload.getHeader(Payload.PROJECT_ID);
        UUID repoId = payload.getHeader(Payload.REPOSITORY_ID);
        if (projectId == null || repoId == null) {
            return chain.process(payload);
        }

        RepositoryEntry repo = repositoryDao.get(projectId, repoId);
        if (repo == null) {
            return chain.process(payload);
        }

        logManager.info(instanceId, "Copying the repository's data: {}", repo);

        Path dst = payload.getHeader(Payload.WORKSPACE_DIR);
        copyRepositoryData(instanceId, projectId, repo, dst);
        RepositoryManager.RepositoryInfo r = repositoryManager.getInfo(repo, dst);

        String branch = Optional.ofNullable(repo.getBranch()).orElse(DEFAULT_BRANCH);

        CommitInfo ci = null;
        if (r != null) {
            ci = new CommitInfo(r.getCommitId(), r.getAuthor(), r.getMessage());
        }

        RepositoryInfo i = new RepositoryInfo(repo.getId(), repo.getName(), repo.getUrl(), branch, repo.getCommitId(), ci);
        payload = payload.putHeader(REPOSITORY_INFO_KEY, i);

        return chain.process(payload);
    }

    private void copyRepositoryData(UUID instanceId, UUID projectId, RepositoryEntry repo, Path dst) {
        repositoryManager.withLock(projectId, repo.getName(), () -> {
            try {
                Path src = repositoryManager.fetch(projectId, repo);

                IOUtils.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
                log.info("process ['{}'] -> copy from {} to {}", instanceId, src, dst);
            } catch (IOException | RepositoryException e) {
                log.error("process ['{}'] -> repository error", instanceId, e);
                logManager.error(instanceId, "Error while copying a repository: " + repo.getUrl(), e);
                throw new ProcessException(instanceId, "Error while copying a repository: " + repo.getUrl(), e);
            }
            return null;
        });
    }

    public static final class RepositoryInfo implements Serializable {

        private final UUID id;
        private final String name;
        private final String url;
        private final String branch;
        private final String commitId;
        private final CommitInfo commitInfo;

        public RepositoryInfo(UUID id, String name, String url, String branch, String commitId,
                              CommitInfo commitInfo) {
            this.id = id;
            this.name = name;
            this.url = url;
            this.branch = branch;
            this.commitId = commitId;
            this.commitInfo = commitInfo;
        }

        public UUID getId() {
            return id;
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

        public String getCommitId() {
            return commitId;
        }

        public CommitInfo getCommitInfo() {
            return commitInfo;
        }

        @Override
        public String toString() {
            return "RepositoryInfo{" +
                    "id=" + id +
                    ", name='" + name + '\'' +
                    ", url='" + url + '\'' +
                    ", branch='" + branch + '\'' +
                    ", commitId='" + commitId + '\'' +
                    ", commitInfo=" + commitInfo +
                    '}';
        }
    }

    public static final class CommitInfo implements Serializable {
        private final String id;
        private final String author;
        private final String message;

        public CommitInfo(String id, String author, String message) {
            this.id = id;
            this.author = author;
            this.message = message;
        }

        public String getId() {
            return id;
        }

        public String getAuthor() {
            return author;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return "CommitInfo{" +
                    "id='" + id + '\'' +
                    ", author='" + author + '\'' +
                    ", message='" + message + '\'' +
                    '}';
        }
    }
}
