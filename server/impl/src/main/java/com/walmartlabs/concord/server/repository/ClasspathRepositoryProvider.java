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

import com.google.common.io.Resources;
import com.walmartlabs.concord.server.org.project.RepositoryEntry;
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
    public RepositoryInfo getInfo(Path path) {
        return null;
    }

    private static String normalizeUrl(String url) {
        return url.substring(URL_PREFIX.length());
    }
}
