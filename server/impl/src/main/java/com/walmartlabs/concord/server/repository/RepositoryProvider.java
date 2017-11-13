package com.walmartlabs.concord.server.repository;

import com.walmartlabs.concord.server.api.project.RepositoryEntry;

import java.nio.file.Path;
import java.util.UUID;

public interface RepositoryProvider {

    void fetch(UUID teamId, RepositoryEntry repository, Path dest);
}
