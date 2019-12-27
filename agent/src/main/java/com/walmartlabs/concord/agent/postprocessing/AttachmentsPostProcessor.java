package com.walmartlabs.concord.agent.postprocessing;

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

import com.walmartlabs.concord.agent.AgentConstants;
import com.walmartlabs.concord.client.ClientUtils;
import com.walmartlabs.concord.client.ProcessApi;
import com.walmartlabs.concord.sdk.Constants;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

@Named
@Singleton
public class AttachmentsPostProcessor extends JobFileUploadPostProcessor {

    @Inject
    public AttachmentsPostProcessor(ProcessApi processApi) {
        super(Constants.Files.JOB_ATTACHMENTS_DIR_NAME,
                "attachments", (instanceId, data) -> {
                    String path = "/api/v1/process/" + instanceId + "/attachment";

                    ClientUtils.withRetry(AgentConstants.API_CALL_MAX_RETRIES, AgentConstants.API_CALL_RETRY_DELAY, () -> {
                        ClientUtils.postData(processApi.getApiClient(), path, data.toFile());
                        return null;
                    });
                });
    }
}
