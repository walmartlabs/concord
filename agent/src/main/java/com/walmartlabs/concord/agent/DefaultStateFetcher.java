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

import com.walmartlabs.concord.client2.ClientUtils;
import com.walmartlabs.concord.client2.ProcessApi;
import com.walmartlabs.concord.common.IOUtils;

import javax.inject.Inject;
import java.io.InputStream;
import java.nio.file.StandardCopyOption;

public class DefaultStateFetcher implements StateFetcher {

    private final ProcessApi processApi;

    @Inject
    public DefaultStateFetcher(ProcessApi processApi) {
        this.processApi = processApi;
    }

    @Override
    public void downloadState(JobRequest job) throws Exception {
        try (InputStream is = ClientUtils.withRetry(AgentConstants.API_CALL_MAX_RETRIES, AgentConstants.API_CALL_RETRY_DELAY, () -> processApi.downloadState(job.getInstanceId()))){
            IOUtils.unzip(is, job.getPayloadDir(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}

