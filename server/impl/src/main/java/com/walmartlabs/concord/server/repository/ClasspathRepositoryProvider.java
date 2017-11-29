package com.walmartlabs.concord.server.repository;

import com.google.common.io.Resources;
import com.walmartlabs.concord.server.api.org.project.RepositoryEntry;
import com.walmartlabs.concord.server.org.project.RepositoryException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

public class ClasspathRepositoryProvider implements RepositoryProvider {

    public static final String URL_PREFIX = "classpath://";

    @Override
    public void fetch(UUID orgId, RepositoryEntry repository, Path dest) {
        String repoUrl = repository.getUrl();
        URL resUrl = Resources.getResource(normalizeUrl(repoUrl));

        try {
            if (!Files.exists(dest)) {
                Files.createDirectories(dest);
            }

            String fileName = repository.getUrl().substring(repository.getUrl().lastIndexOf('/') + 1);
            Path destFile = dest.resolve(fileName);

            try (OutputStream out = Files.newOutputStream(destFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                Resources.copy(resUrl, out);
            }
        } catch (IOException e) {
            throw new RepositoryException("Error while fetching a repository", e);
        }
    }

    @Override
    public RepositoryManager.RepositoryInfo getInfo(Path path) {
        return null;
    }

    private static String normalizeUrl(String url) {
        return url.substring(URL_PREFIX.length());
    }
}