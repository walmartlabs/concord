package com.walmartlabs.concord.agent;

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

import com.walmartlabs.concord.agent.executors.JobExecutor;
import com.walmartlabs.concord.agent.executors.JobExecutorFactory;
import com.walmartlabs.concord.agent.logging.ProcessLog;
import com.walmartlabs.concord.agent.remote.ProcessStatusUpdater;
import com.walmartlabs.concord.imports.ImportManager;

import javax.inject.Inject;

public class WorkerFactory {

    private final RepositoryManager repositoryManager;
    private final ImportManager importManager;
    private final JobExecutorFactory jobExecutorFactory;
    private final StateFetcher stateFetcher;
    private final ProcessStatusUpdater statusUpdater;
    private final ProcessLog processLog;

    @Inject
    public WorkerFactory(RepositoryManager repositoryManager,
                         ImportManager importManager,
                         JobExecutorFactory jobExecutorFactory,
                         StateFetcher stateFetcher,
                         ProcessStatusUpdater statusUpdater,
                         ProcessLog processLog) {

        this.repositoryManager = repositoryManager;
        this.importManager = importManager;
        this.jobExecutorFactory = jobExecutorFactory;
        this.stateFetcher = stateFetcher;
        this.statusUpdater = statusUpdater;
        this.processLog = processLog;
    }

    public Worker create(JobRequest jobRequest, Worker.CompletionCallback completionCallback) {
        JobExecutor executor = jobExecutorFactory.create(jobRequest.getType());

        return new Worker(repositoryManager, importManager, executor, completionCallback, stateFetcher, statusUpdater, processLog, jobRequest);
    }
}
