package com.walmartlabs.concord.agent;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.dependencymanager.DependencyManager;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.client.ProcessApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ExecutionManager {

    private static final Logger log = LoggerFactory.getLogger(ExecutionManager.class);
    private static final long JOB_ENTRY_TTL = 8 * 60 * 60 * 1000; // 8 hours

    private final JobExecutor jobExecutor;
    private final LogManager logManager;
    private final Configuration cfg;

    private final Cache<UUID, ProcessStatus> statuses = CacheBuilder.newBuilder()
            .expireAfterAccess(JOB_ENTRY_TTL, TimeUnit.MILLISECONDS)
            .build();

    private final Cache<UUID, JobInstance> instances = CacheBuilder.newBuilder()
            .expireAfterAccess(JOB_ENTRY_TTL, TimeUnit.MILLISECONDS)
            .build();

    private final Object mutex = new Object();

    public ExecutionManager(Configuration cfg, ProcessApi processApi) {

        this.logManager = new LogManager(cfg);
        this.cfg = cfg;

        DependencyManager dependencyManager = new DependencyManager(cfg.getDependencyCacheDir());
        this.jobExecutor = new DefaultJobExecutor(cfg, logManager, dependencyManager, new ProcessApiClient(cfg, processApi));
    }

    public JobInstance start(UUID instanceId, String entryPoint, Path payload) throws ExecutionException {
        // remove the previous log file
        // e.g. left after the execution was suspended
        logManager.delete(instanceId);
        logManager.touch(instanceId);

        logManager.log(instanceId, "Agent ID: %s", cfg.getAgentId());

        Path tmpDir = extract(payload);

        synchronized (mutex) {
            statuses.put(instanceId, ProcessStatus.RUNNING);
        }

        JobInstance i;
        try {
            i = jobExecutor.start(instanceId, tmpDir, entryPoint);
        } catch (Exception e) {
            log.warn("start ['{}', {}] -> failed", instanceId, entryPoint, e);
            handleError(instanceId);
            throw e;
        }

        synchronized (mutex) {
            instances.put(instanceId, i);
        }

        CompletableFuture<?> f = i.future();
        f.thenRun(() -> {
            synchronized (mutex) {
                statuses.put(instanceId, ProcessStatus.FINISHED);
            }
        }).exceptionally(e -> {
            handleError(instanceId);
            return null;
        });

        return i;
    }

    private void handleError(UUID instanceId) {
        synchronized (mutex) {
            ProcessStatus s = statuses.getIfPresent(instanceId);
            if (s != ProcessStatus.CANCELLED) {
                statuses.put(instanceId, ProcessStatus.FAILED);
            }
        }
    }

    public void cancel(UUID id) {
        synchronized (mutex) {
            ProcessStatus s = statuses.getIfPresent(id);
            if (s != null && s == ProcessStatus.RUNNING) {
                statuses.put(id, ProcessStatus.CANCELLED);
            }
        }

        JobInstance i = instances.getIfPresent(id);
        if (i == null) {
            return;
        }

        i.cancel();
    }

    public ProcessStatus getStatus(UUID id) {
        ProcessStatus s;
        synchronized (mutex) {
            s = statuses.getIfPresent(id);
        }

        if (s == null) {
            throw new IllegalArgumentException("Unknown execution ID: " + id);
        }

        return s;
    }

    public boolean isRunning(String id) {
        ProcessStatus s;
        synchronized (mutex) {
            s = statuses.getIfPresent(id);
        }

        if (s == null) {
             return false;
        }

        return s == ProcessStatus.RUNNING;
    }

    public void cleanup() {
        synchronized (mutex) {
            statuses.cleanUp();
            instances.cleanUp();
        }
    }

    private Path extract(Path in) throws ExecutionException {
        Path baseDir = cfg.getPayloadDir();
        try {
            Path dst = IOUtils.createTempDir(baseDir, "workDir");
            Files.createDirectories(dst);
            IOUtils.unzip(in, dst);
            return dst;
        } catch (IOException e) {
            throw new ExecutionException("Error while unpacking a payload", e);
        }
    }
}
