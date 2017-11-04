package com.walmartlabs.concord.server.repository;

import com.walmartlabs.concord.server.api.project.RepositoryEntry;

import java.nio.file.Path;

public interface RepositoryProvider {

    void fetch(RepositoryEntry repository, Path dest);
}
