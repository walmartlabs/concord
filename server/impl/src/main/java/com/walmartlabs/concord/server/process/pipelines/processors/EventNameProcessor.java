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
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Named
public class EventNameProcessor implements PayloadProcessor {

    private final ProcessLogManager logManager;

    @Inject
    public EventNameProcessor(ProcessLogManager logManager) {
        this.logManager = logManager;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Payload process(Chain chain, Payload payload) {
        String eventName = payload.getHeader(Payload.EVENT_NAME);
        if (eventName == null) {
            return chain.process(payload);
        }

        Map<String, Object> cfg = payload.getHeader(Payload.CONFIGURATION, Collections.emptyMap());
        if (cfg == null) {
            cfg = new HashMap<>();
        }

        Map<String, Object> args = (Map<String, Object>) cfg.get(Constants.Request.ARGUMENTS_KEY);
        if (args == null) {
            args = new HashMap<>();
            cfg.put(Constants.Request.ARGUMENTS_KEY, args);
        }

        String old = (String) args.get(Constants.Request.EVENT_NAME_KEY);
        if (old != null) {
            // don't overwrite the existing value for backward-compatibility reasons
            logManager.warn(payload.getProcessKey(), "Can't overwrite the system variable '{}', the value already exists.", Constants.Request.EVENT_NAME_KEY);
            return chain.process(payload);
        }

        args.put(Constants.Request.EVENT_NAME_KEY, eventName);

        payload = payload.putHeader(Payload.CONFIGURATION, cfg);

        return chain.process(payload);
    }
}
