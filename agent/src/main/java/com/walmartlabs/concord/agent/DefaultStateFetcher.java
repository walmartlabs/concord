package com.walmartlabs.concord.agent;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.client.ClientUtils;
import com.walmartlabs.concord.client.ProcessApi;
import com.walmartlabs.concord.common.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class DefaultStateFetcher implements StateFetcher {

    private static final Logger log = LoggerFactory.getLogger(DefaultStateFetcher.class);

    private final ProcessApi processApi;

    @Inject
    public DefaultStateFetcher(ProcessApi processApi) {
        this.processApi = processApi;
    }

    @Override
    public void downloadState(JobRequest job) throws Exception {
        File payload = null;
        try {
            payload = ClientUtils.withRetry(AgentConstants.API_CALL_MAX_RETRIES, AgentConstants.API_CALL_RETRY_DELAY, () -> processApi.downloadState(job.getInstanceId()));
            IOUtils.unzip(payload.toPath(), job.getPayloadDir(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            if (payload != null) {
                delete(payload.toPath());
            }
        }
    }

    private static void delete(Path dir) {
        if (dir == null) {
            return;
        }

        try {
            IOUtils.deleteRecursively(dir);
        } catch (Exception e) {
            log.warn("delete ['{}'] -> error", dir, e);
        }
    }
}
