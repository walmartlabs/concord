package com.walmartlabs.concord.server.process.pipelines.processors;

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

import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.process.PartialProcessKey;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessEntry;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.UUID;

import static com.walmartlabs.concord.server.process.pipelines.processors.RepositoryProcessor.REPOSITORY_INFO_KEY;

/**
 * Adds the parent process' repository info to the forked process configuration.
 * This information will be used by the Agent to pull the repository files into
 * the fork's workspace.
 */
@Named
public class ForkRepositoryInfoProcessor implements PayloadProcessor {

    private final ProcessQueueDao queueDao;

    @Inject
    public ForkRepositoryInfoProcessor(ProcessQueueDao queueDao) {
        this.queueDao = queueDao;
    }

    @Override
    @WithTimer
    public Payload process(Chain chain, Payload payload) {
        UUID parentInstanceId = payload.getHeader(Payload.PARENT_INSTANCE_ID);

        ProcessEntry parent = queueDao.get(PartialProcessKey.from(parentInstanceId));
        if (parent == null) {
            throw new ProcessException(payload.getProcessKey(), "Parent process '" + parentInstanceId + "' not found");
        }

        RepositoryProcessor.CommitInfo ci = new RepositoryProcessor.CommitInfo(parent.commitId(), null, parent.commitMsg());
        RepositoryProcessor.RepositoryInfo i = new RepositoryProcessor.RepositoryInfo(
                parent.repoId(), parent.repoName(), parent.repoUrl(),
                parent.repoPath(), null, parent.commitId(), ci);

        payload = payload.putHeader(REPOSITORY_INFO_KEY, i);

        return chain.process(payload);
    }
}
