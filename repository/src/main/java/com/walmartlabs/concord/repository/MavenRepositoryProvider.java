package com.walmartlabs.concord.repository;

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.dependencymanager.DependencyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * *****
 * Concord
 * -----
 * Copyright (C)  - 2025 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */
public class MavenRepositoryProvider implements RepositoryProvider {

    private static final String URL_PREFIX = "mvn://";
    private static final Logger log = LoggerFactory.getLogger(MavenRepositoryProvider.class);
    private final DependencyManager dependencyManager;

    public MavenRepositoryProvider(DependencyManager dependencyManager) {
        this.dependencyManager = dependencyManager;
    }

    /**
     * @param url maven repo url in format mvn://groupId:artifactId
     * @return boolean can handle or not
     */
    @Override
    public boolean canHandle(String url) {
        return url.startsWith(URL_PREFIX);
    }

    /**
     * @param request fetchRequest
     * @return fetchResult
     */
    @Override
    public FetchResult fetch(FetchRequest request) {
        Path dst = request.destination();
        try {
            URI uri = new URI(request.url().concat(":").concat(request.version().value()));
            Path dependencyPath = dependencyManager.resolveSingle(uri).getPath();
            IOUtils.copy(dependencyPath, dst, StandardCopyOption.REPLACE_EXISTING);
            return null;
        } catch (URISyntaxException | IOException e) {
            try {
                IOUtils.deleteRecursively(request.destination());
            } catch (IOException ee) {
                log.warn("fetch ['{}', '{}', '{}'] -> cleanup error: {}",
                        request.url(), request.version(), request.destination(), e.getMessage());
            }
            throw new RepositoryException("Error while fetching a repository", e);
        }
    }

    /**
     * @param src source of the fetched repo
     * @param dst destination to be copied to
     * @param ignorePatterns ignore some files while copying
     * @return  snapshot of copied files
     * @throws IOException exception occurred during copy
     */
    @Override
    public Snapshot export(Path src, Path dst, List<String> ignorePatterns) throws IOException {
        LastModifiedSnapshot snapshot = new LastModifiedSnapshot();
        IOUtils.unzip(src, dst, false, snapshot, StandardCopyOption.REPLACE_EXISTING);
        return snapshot;
    }
}
