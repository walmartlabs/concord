package com.walmartlabs.concord.server.project;

import com.walmartlabs.concord.server.api.project.ProjectEntry;
import com.walmartlabs.concord.server.api.project.RepositoryEntry;
import org.sonatype.siesta.ValidationErrorsException;

import java.util.Map;

public final class RepositoryUtils {

    public static RepositoryEntry assertRepository(ProjectEntry p, String repositoryName) {
        if (repositoryName == null) {
            throw new ValidationErrorsException("Invalid repository name");
        }

        Map<String, RepositoryEntry> repos = p.getRepositories();
        if (repos == null || repos.isEmpty()) {
            throw new ValidationErrorsException("Repository not found: " + repositoryName);
        }

        RepositoryEntry r = repos.get(repositoryName);
        if (r == null) {
            throw new ValidationErrorsException("Repository not found: " + repositoryName);
        }

        return r;
    }

    private RepositoryUtils() {
    }
}
