package com.walmartlabs.concord.server.process.pipelines.processors;

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

import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;

import javax.inject.Inject;
import javax.inject.Named;

import static com.walmartlabs.concord.server.process.pipelines.processors.RepositoryProcessor.REPOSITORY_INFO_KEY;

/**
 * Updates the process queue entry according to the current process' repository data.
 */
@Named
public class RepositoryInfoUpdateProcessor implements PayloadProcessor {

    private final ProcessQueueDao queueDao;

    @Inject
    public RepositoryInfoUpdateProcessor(ProcessQueueDao queueDao) {
        this.queueDao = queueDao;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        RepositoryProcessor.RepositoryInfo i = payload.getHeader(REPOSITORY_INFO_KEY);
        if (i == null) {
            return chain.process(payload);
        }

        String commitId = null;
        String commitBranch = null;

        RepositoryProcessor.CommitInfo ci = i.getCommitInfo();
        if (ci != null) {
            commitId = ci.getId();
            commitBranch = ci.getBranch();
        }

        queueDao.updateRepositoryDetails(payload.getProcessKey(), i.getId(), i.getUrl(), i.getPath(), commitId, commitBranch);

        return chain.process(payload);
    }
}
