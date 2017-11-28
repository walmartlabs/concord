package com.walmartlabs.concord.server.repository;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.server.api.org.project.RepositoryEntry;
import com.walmartlabs.concord.server.cfg.RepositoryConfiguration;
import com.walmartlabs.concord.server.org.project.RepositoryException;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Named
public class RepositoryMetaManager {

    private static final String DEFAULT_BRANCH = "master";
    private static final String META_FILE = "meta.json";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final RepositoryConfiguration cfg;

    @Inject
    public RepositoryMetaManager(RepositoryConfiguration cfg) {
        this.cfg = cfg;
    }

    public RepositoryMeta readMeta(UUID projectId, RepositoryEntry repository)  {
        Path p = getMetaPath(projectId, repository);

        if (!Files.exists(p)) {
            return null;
        }

        Path metaPath = p.resolve(META_FILE);
        try (InputStream in = Files.newInputStream(metaPath)) {
            return objectMapper.readValue(in, RepositoryMeta.class);
        } catch (IOException e) {
            throw new RepositoryException("read repository meta error", e);
        }
    }

    public void writeMeta(UUID projectId, RepositoryEntry repository, RepositoryMeta meta) {
        Path p = getMetaPath(projectId, repository);

        if (!Files.exists(p)) {
            try {
                Files.createDirectories(p);
            } catch (IOException e) {
                throw new RepositoryException("Can't create a directory for a meta", e);
            }
        }

        Path metaPath = p.resolve(META_FILE);
        try (OutputStream os = Files.newOutputStream(metaPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            objectMapper.writeValue(os, meta);
        } catch (Exception e) {
            throw new RepositoryException("write repository meta error", e);
        }
    }

    private Path getMetaPath(UUID projectId, RepositoryEntry repository) {
        String branch;
        if (repository.getCommitId() != null) {
            branch = repository.getCommitId();
        } else {
            branch = Optional.ofNullable(repository.getBranch()).orElse(DEFAULT_BRANCH);
        }

        return cfg.getRepoMetaDir()
                .resolve(String.valueOf(projectId))
                .resolve(repository.getName())
                .resolve(branch);
    }

    public static class RepositoryMeta implements Serializable {

        private final Date pushDate;

        public RepositoryMeta(@JsonProperty("pushDate") Date pushDate) {
            this.pushDate = pushDate;
        }

        public Date getPushDate() {
            return pushDate;
        }
    }
}