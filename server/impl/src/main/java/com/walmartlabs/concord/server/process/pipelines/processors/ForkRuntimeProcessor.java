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
import com.walmartlabs.concord.server.sdk.PartialProcessKey;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.UUID;

/**
 * Get the runtime value from the parent process.
 */
@Named
public class ForkRuntimeProcessor implements PayloadProcessor {

    private final ProcessQueueDao queueDao;

    @Inject
    public ForkRuntimeProcessor(ProcessQueueDao queueDao) {
        this.queueDao = queueDao;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        UUID parentInstanceId = payload.getHeader(Payload.PARENT_INSTANCE_ID);
        if (parentInstanceId == null) {
            return chain.process(payload);
        }

        String runtime = queueDao.getRuntime(PartialProcessKey.from(parentInstanceId));
        payload = payload.putHeader(Payload.RUNTIME, runtime);

        return chain.process(payload);
    }
}
