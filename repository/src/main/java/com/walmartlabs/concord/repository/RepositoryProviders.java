package com.walmartlabs.concord.repository;

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

import com.google.common.util.concurrent.Striped;
import com.walmartlabs.concord.sdk.Secret;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

public class RepositoryProviders {

    private final Striped<Lock> locks = Striped.lock(64);

    private final List<RepositoryProvider> providers;
    private final long lockTimeout;

    public RepositoryProviders(List<RepositoryProvider> providers, long lockTimeout) {
        this.providers = providers;
        this.lockTimeout = lockTimeout;
    }

    public Repository fetch(String uri, String branch, String commitId, String path, Secret secret, Path cacheDir) {
        String encodedUrl = encodeUrl(uri);
        Path localPath = cacheDir.resolve(encodedUrl);

        RepositoryProvider provider = getProvider(uri);
        provider.fetch(uri, branch, commitId, secret, localPath);

        Path repoPath = repoPath(localPath, path);

        return new Repository(provider.getBranchOrDefault(branch), localPath, repoPath, provider);
    }

    public <T> T withLock(String repoUrl, Callable<T> f) {
        Lock l = locks.get(repoUrl);
        try {
            if (!l.tryLock(lockTimeout, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Timeout waiting for the repository lock. Repository url: " + repoUrl);
            }
            return f.call();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            l.unlock();
        }
    }

    private RepositoryProvider getProvider(String url) {
        return providers.stream()
                .filter(p -> p.canHandle(url))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Can't find provider for '" + url + "'"));
    }

    private static String normalizePath(String s) {
        if (s == null) {
            return null;
        }

        while (s.startsWith("/")) {
            s = s.substring(1);
        }

        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }

        if (s.trim().isEmpty()) {
            return null;
        }

        return s;
    }

    private static Path repoPath(Path baseDir, String p) {
        String normalized = normalizePath(p);
        if (normalized == null) {
            return baseDir;
        }

        Path repoDir = baseDir.resolve(normalized);
        if (!Files.exists(repoDir)) {
            throw new RepositoryException("Invalid repository path: '" + p + "' doesn't exist");
        } else if (!repoDir.toFile().isDirectory()) {
            throw new RepositoryException("Invalid repository path: '" + p + "' must be a valid directory");
        }

        return repoDir;
    }

    private static String encodeUrl(String url) {
        String encodedUrl;
        try {
            encodedUrl = URLEncoder.encode(url, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RepositoryException("Url encoding error", e);
        }

        return encodedUrl;
    }
}
