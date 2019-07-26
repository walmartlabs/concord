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

import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.RequestId;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessKey;
import com.walmartlabs.concord.server.process.ProcessKind;
import com.walmartlabs.concord.server.process.TriggeredByEntry;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Named
public class InitialQueueEntryProcessor implements PayloadProcessor {

    private final ProcessQueueDao queueDao;

    @Inject
    public InitialQueueEntryProcessor(ProcessQueueDao queueDao) {
        this.queueDao = queueDao;
    }

    @Override
    @WithTimer
    @SuppressWarnings("unchecked")
    public Payload process(Chain chain, Payload payload) {
        ProcessKey processKey = payload.getProcessKey();
        ProcessKind kind = payload.getHeader(Payload.PROCESS_KIND, ProcessKind.DEFAULT);
        UUID projectId = payload.getHeader(Payload.PROJECT_ID);
        UUID parentInstanceId = payload.getHeader(Payload.PARENT_INSTANCE_ID);
        UUID initiatorId = payload.getHeader(Payload.INITIATOR_ID);
        Map<String, Object> cfg = payload.getHeader(Payload.REQUEST_DATA_MAP, Collections.emptyMap());
        Map<String, Object> meta = getMeta(cfg);
        String exclusiveGroup = payload.getHeader(Payload.EXCLUSIVE_GROUP);
        TriggeredByEntry triggeredBy = payload.getHeader(Payload.TRIGGERED_BY);

        queueDao.insertInitial(processKey, kind, parentInstanceId, projectId, initiatorId, meta, exclusiveGroup, triggeredBy);

        return chain.process(payload);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMeta(Map<String, Object> cfg) {
        Map<String, Object> m = (Map<String, Object>) cfg.get(Constants.Request.META);
        if (m == null) {
            m = Collections.emptyMap();
        }

        m = new HashMap<>(m);
        m.put(Constants.Meta.SYSTEM_GROUP, Collections.singletonMap(Constants.Meta.REQUEST_ID, RequestId.get()));

        return m;
    }
}
