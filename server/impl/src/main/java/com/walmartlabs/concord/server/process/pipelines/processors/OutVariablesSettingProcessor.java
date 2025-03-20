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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class OutVariablesSettingProcessor implements PayloadProcessor {

    @Override
    public Payload process(Chain chain, Payload payload) {
        Set<String> outExpr = payload.getHeader(Payload.OUT_EXPRESSIONS);
        if (outExpr == null || outExpr.isEmpty()) {
            return chain.process(payload);
        }

        Map<String, Object> cfg = payload.getHeader(Payload.CONFIGURATION);
        if (cfg == null) {
            cfg = new HashMap<>();
        }

        cfg.put(Constants.Request.OUT_EXPRESSIONS_KEY, outExpr);
        payload = payload.mergeValues(Payload.CONFIGURATION,
                Collections.singletonMap(Constants.Request.OUT_EXPRESSIONS_KEY, outExpr));

        return chain.process(payload);
    }
}
