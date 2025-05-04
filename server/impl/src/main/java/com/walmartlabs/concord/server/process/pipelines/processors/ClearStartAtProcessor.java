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

import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

/**
 * Removes the "startAt" parameter from the process configuration and
 * the process queue entry. Necessary when resuming a process - it doesn't make
 * any sense to re-use the same "startAt" value which might be in the past
 * already.
 */
public class ClearStartAtProcessor implements PayloadProcessor {

    private final ProcessQueueDao queueDao;

    @Inject
    public ClearStartAtProcessor(ProcessQueueDao queueDao) {
        this.queueDao = queueDao;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        payload = clearConfiguration(payload);
        queueDao.clearStartAt(payload.getProcessKey());
        return chain.process(payload);
    }

    private static Payload clearConfiguration(Payload payload) {
        Map<String, Object> cfg = payload.getHeader(Payload.CONFIGURATION);
        if (cfg == null) {
            return payload;
        }

        Map<String, Object> m = new HashMap<>(cfg);
        m.remove(Constants.Request.START_AT_KEY);
        return payload.putHeader(Payload.CONFIGURATION, m);
    }
}
