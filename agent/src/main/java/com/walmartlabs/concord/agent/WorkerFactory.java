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
import com.walmartlabs.concord.imports.ImportManager;

import java.util.Map;

public class WorkerFactory {

    private final RepositoryManager repositoryManager;
    private final ImportManager importManager;
    private final Map<JobRequest.Type, JobExecutor> executors;

    public WorkerFactory(RepositoryManager repositoryManager, ImportManager importManager, Map<JobRequest.Type, JobExecutor> executors) {
        this.repositoryManager = repositoryManager;
        this.importManager = importManager;
        this.executors = executors;
    }

    public Worker create(JobRequest jobRequest, Worker.CompletionCallback completionCallback, Worker.StateFetcher stateFetcher) throws ExecutionException {
        JobExecutor executor = executors.get(jobRequest.getType());
        if (executor == null) {
            throw new ExecutionException("Unsupported job type: " + jobRequest.getType());
        }

        return new Worker(repositoryManager, importManager, executor, completionCallback, stateFetcher, jobRequest);
    }
}
