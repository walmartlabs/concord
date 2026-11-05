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

import com.walmartlabs.concord.common.PathUtils;
import com.walmartlabs.concord.repository.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class ClasspathRepositoryProvider implements RepositoryProvider {

    private static final String URL_PREFIX = "classpath://";

    @Override
    public boolean canHandle(String url) {
        return url.startsWith(URL_PREFIX);
    }

    @Override
    public FetchResult fetch(FetchRequest request) {
        String repoUrl = request.url();
        Path dst = request.destination();
        URL resUrl = getResource(normalizeUrl(repoUrl));

        try {
            if (!Files.exists(dst)) {
                Files.createDirectories(dst);
            }

            String fileName = repoUrl.substring(repoUrl.lastIndexOf('/') + 1);
            Path dstFile = dst.resolve(fileName);

            try (InputStream in = resUrl.openStream();
                 OutputStream out = Files.newOutputStream(dstFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                in.transferTo(out);
            }
            return null;
        } catch (IOException e) {
            throw new RepositoryException("Error while fetching a repository", e);
        }
    }

    @Override
    public Snapshot export(Path src, Path dst, List<String> ignorePatterns) throws IOException {
        PathUtils.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
        return Snapshot.includeAll();
    }

    private static URL getResource(String name) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = ClasspathRepositoryProvider.class.getClassLoader();
        }

        URL url = cl.getResource(name);
        if (url == null) {
            throw new IllegalArgumentException("resource " + name + " not found.");
        }
        return url;
    }

    private static String normalizeUrl(String url) {
        return url.substring(URL_PREFIX.length());
    }
}
