package com.walmartlabs.concord.server.repository;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.server.org.project.RepositoryEntry;
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

        return cfg.getMetaDir()
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
