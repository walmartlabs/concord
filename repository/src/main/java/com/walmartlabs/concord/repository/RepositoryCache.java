package com.walmartlabs.concord.repository;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.Striped;
import com.walmartlabs.concord.common.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

public class RepositoryCache {

    private static final Logger log = LoggerFactory.getLogger(RepositoryCache.class);

    private final Path cacheDir;
    private final RepositoryAccessJournal accessJournal;
    private final long lockTimeout;
    private final long maxCacheAge;

    private final Striped<Lock> locks;

    public RepositoryCache(Path cacheDir,
                           Path repoJournalPath,
                           Duration lockTimeout,
                           Duration maxCacheAge,
                           int lockCount,
                           ObjectMapper objectMapper) throws IOException {

        this.cacheDir = cacheDir;
        this.lockTimeout = lockTimeout.toMillis();
        this.accessJournal = maxCacheAge.toMillis() > 0 ? new RepositoryAccessJournal(objectMapper, repoJournalPath) : null;
        this.maxCacheAge = maxCacheAge.toMillis();
        this.locks = Striped.lock(lockCount);
    }

    public Path getPath(String repositoryUrl) {
        String encodedUrl = encodeUrl(repositoryUrl);
        Path repoPath = cacheDir.resolve(encodedUrl);

        if (accessJournal != null) {
            try {
                accessJournal.recordAccess(repositoryUrl, repoPath);
            } catch (IOException e) {
                throw new RepositoryException("Error while writing repository cache info", e);
            }
        }
        
        return repoPath;
    }

    public <T> T withLock(String repoUrl, Callable<T> f) {
        return withLock(lockTimeout, repoUrl, f);
    }

    private <T> T withLock(long lockTimeout, String repoUrl, Callable<T> f) {
        Lock l = locks.get(repoUrl);
        try {
            if (l.tryLock(lockTimeout, TimeUnit.MILLISECONDS)) {
                try {
                    return f.call();
                } catch (IllegalArgumentException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e.getMessage(), e);
                } finally {
                    l.unlock();
                }
            }
            throw new IllegalStateException("Timeout waiting for the repository lock. Repository url: " + repoUrl);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    public void cleanup() {
        if (maxCacheAge == 0) {
            return;
        }

        List<RepositoryAccessJournal.RepositoryJournalItem> oldItems = accessJournal.listOld(maxCacheAge);
        for (RepositoryAccessJournal.RepositoryJournalItem i : oldItems) {
            Path repoPath = withLock(lockTimeout, i.repoUrl(), () -> {
                try {
                    Path tmpDir = null;

                    if (Files.exists(i.repoPath())) {
                        tmpDir = i.repoPath().getParent().resolve(i.repoPath().getFileName() + ".tmp");
                        Files.move(i.repoPath(), tmpDir);
                    }
                    
                    accessJournal.removeRecord(i.repoUrl());
                    return tmpDir;
                } catch (IOException e) {
                    log.warn("cleanup ['{}'] -> move error", i.repoPath(), e);
                }
                return null;
            });

            if (repoPath != null) {
                try {
                    PathUtils.deleteRecursively(repoPath);
                } catch (IOException e) {
                    log.warn("cleanup ['{}'] -> delete error", i.repoPath(), e);
                }
            }
        }

        log.info("cleanup -> {} repositories removed", oldItems.size());
    }

    public long cleanupInterval() {
        return maxCacheAge;
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
