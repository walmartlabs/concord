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
import com.walmartlabs.concord.common.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.Path;
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

    private long nextCleanup = -1;

    public RepositoryCache(Path cacheDir,
                           Path repoJournalPath,
                           long lockTimeout,
                           long maxCacheAge,
                           int lockCount,
                           ObjectMapper objectMapper) throws IOException {

        this.cacheDir = cacheDir;
        this.lockTimeout = lockTimeout;
        this.accessJournal = maxCacheAge > 0 ? new RepositoryAccessJournal(objectMapper, repoJournalPath) : null;
        this.maxCacheAge = maxCacheAge;
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
        try {
            return withLock(lockTimeout, repoUrl, f);
        } finally {
            cleanup();
        }
    }

    private <T> T withLock(long lockTimeout, String repoUrl, Callable<T> f) {
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

    private void cleanup() {
        if (maxCacheAge == 0) {
            return;
        }

        long now = System.currentTimeMillis();
        if (nextCleanup >= now) {
            return;
        }

        List<RepositoryAccessJournal.RepositoryJournalItem> oldItems = accessJournal.listOld(maxCacheAge);
        for (RepositoryAccessJournal.RepositoryJournalItem i : oldItems) {
            withLock(lockTimeout, i.repoUrl(), () -> {
                try {
                    IOUtils.deleteRecursively(i.repoPath());
                    accessJournal.removeRecord(i.repoUrl());
                } catch (IOException e) {
                    log.warn("cleanup ['{}'] -> error", i.repoPath(), e);
                }
                return null;
            });
        }

        nextCleanup += maxCacheAge;

        log.info("cleanup -> {} repositories removed", oldItems.size());
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
