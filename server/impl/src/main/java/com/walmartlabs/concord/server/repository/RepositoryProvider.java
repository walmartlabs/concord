package com.walmartlabs.concord.server.repository;

import com.walmartlabs.concord.server.api.org.project.RepositoryEntry;

import java.nio.file.Path;
import java.util.UUID;

public interface RepositoryProvider {

    void fetch(UUID orgId, RepositoryEntry repository, Path dest);

    RepositoryManager.RepositoryInfo getInfo(Path path);
}
