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
import com.walmartlabs.concord.server.process.Payload;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Updates the fork's list of handlers according to the process arguments.
 */
public class ForkHandlersProcessor implements PayloadProcessor {

    @Override
    public Payload process(Chain chain, Payload payload) {
        Set<String> handlers = payload.getHeader(Payload.PROCESS_HANDLERS);
        handlers = new HashSet<>(handlers != null ? handlers : Collections.emptySet());

        update(payload, handlers, Constants.Flows.ON_FAILURE_FLOW, Constants.Request.DISABLE_ON_FAILURE_KEY);
        update(payload, handlers, Constants.Flows.ON_CANCEL_FLOW, Constants.Request.DISABLE_ON_CANCEL_KEY);
        update(payload, handlers, Constants.Flows.ON_TIMEOUT_FLOW, Constants.Request.DISABLE_ON_TIMEOUT_KEY);

        payload = payload.putHeader(Payload.PROCESS_HANDLERS, handlers);

        return chain.process(payload);
    }

    private static void update(Payload payload, Set<String> handlers, String flow, String disableFlag) {
        boolean suppressed = getBoolean(payload, disableFlag);
        if (suppressed) {
            handlers.remove(flow);
        }
    }

    private static boolean getBoolean(Payload payload, String key) {
        Map<String, Object> cfg = payload.getHeader(Payload.CONFIGURATION);
        if (cfg == null) {
            return false;
        }

        Object v = cfg.get(key);
        if (v == null) {
            return false;
        }

        return (Boolean) v;
    }
}
