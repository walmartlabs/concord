package com.walmartlabs.concord.server.project;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.server.cfg.RepositoryConfiguration;

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
import java.util.UUID;

import static com.walmartlabs.concord.server.project.RepositoryManagerImpl.DEFAULT_BRANCH;

@Named
public class RepositoryMetaManager {

    private static final String META_FILE = "meta.json";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final RepositoryConfiguration cfg;

    @Inject
    public RepositoryMetaManager(RepositoryConfiguration cfg) {
        this.cfg = cfg;
    }

    public RepositoryMeta readMeta(UUID projectId, String repoName, String branch) throws IOException {
        Path p = getMetaPath(projectId, repoName, branch);

        if (!Files.exists(p)) {
            return null;
        }

        Path metaPath = p.resolve(META_FILE);
        try (InputStream in = Files.newInputStream(metaPath)) {
            return objectMapper.readValue(in, RepositoryMeta.class);
        }
    }

    public void writeMeta(UUID projectId, String repoName, String branch, RepositoryMeta meta) throws IOException {
        Path p = getMetaPath(projectId, repoName, branch);

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
        }
    }

    private Path getMetaPath(UUID projectId, String repoName, String branch) {
        if (branch == null) {
            branch = DEFAULT_BRANCH;
        }

        return cfg.getRepoMetaDir()
                .resolve(String.valueOf(projectId))
                .resolve(repoName)
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