package com.walmartlabs.concord.agent;

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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.walmartlabs.concord.client.ProcessApi;
import com.walmartlabs.concord.dependencymanager.DependencyManager;
import com.walmartlabs.concord.project.InternalConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class ExecutionManager {

    private static final Logger log = LoggerFactory.getLogger(ExecutionManager.class);
    private static final long JOB_ENTRY_TTL = 8 * 60 * 60 * 1000L; // 8 hours

    private final DefaultJobExecutor jobExecutor;
    private final LogManager logManager;
    private final Configuration cfg;

    private final Cache<UUID, JobInstance> instances = CacheBuilder.newBuilder()
            .expireAfterAccess(JOB_ENTRY_TTL, TimeUnit.MILLISECONDS)
            .build();

    public ExecutionManager(Configuration cfg, ProcessApi processApi) throws IOException {

        this.logManager = new LogManager(cfg);
        this.cfg = cfg;

        DependencyManager dependencyManager = new DependencyManager(cfg.getDependencyCacheDir());
        ProcessApiClient client = new ProcessApiClient(cfg, processApi);
        this.jobExecutor = new DefaultJobExecutor(cfg, logManager, dependencyManager, createPostProcessors(client));
    }

    public JobInstance start(UUID instanceId, String entryPoint, Path payload) {
        // remove the previous log file
        // e.g. left after the execution was suspended
        logManager.delete(instanceId);
        logManager.touch(instanceId);

        logManager.log(instanceId, "Agent ID: %s", cfg.getAgentId());

        JobInstance i;
        try {
            i = jobExecutor.start(instanceId, payload);
        } catch (Exception e) {
            log.warn("start ['{}', {}] -> failed", instanceId, entryPoint, e);
            throw e;
        }

        instances.put(instanceId, i);

        return i;
    }

    public void cancel(UUID id) {
        JobInstance i = instances.getIfPresent(id);
        if (i == null) {
            log.info("cancel ['{}'] -> not found", id);
            return;
        }

        i.cancel();
        log.info("cancel ['{}'] -> done", id);
    }

    public boolean isRunning(UUID id) {
        JobInstance i = instances.getIfPresent(id);
        if (i == null) {
            return false;
        }

        return !i.future().isDone();
    }

    public void cleanup() {
        instances.cleanUp();
    }

    private static List<JobPostProcessor> createPostProcessors(ProcessApiClient client) {
        return Collections.singletonList(
                new JobFileUploadPostProcessor(InternalConstants.Files.JOB_ATTACHMENTS_DIR_NAME,
                        "attachments", client::uploadAttachments)
        );
    }
}
