package com.walmartlabs.concord.plugins.nexus;

import java.io.Serializable;

public class Configuration implements Serializable {

    private final String repositoryFilter;
    private final String artifactFilter;

    public Configuration(String repositoryFilter, String artifactFilter) {
        this.repositoryFilter = repositoryFilter;
        this.artifactFilter = artifactFilter;
    }

    public String getRepositoryFilter() {
        return repositoryFilter;
    }

    public String getArtifactFilter() {
        return artifactFilter;
    }
}
