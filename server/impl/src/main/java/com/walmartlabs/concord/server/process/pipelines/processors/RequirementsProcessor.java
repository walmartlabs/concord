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

import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.server.process.Payload;

import javax.inject.Named;
import java.util.Map;

@Named
public class RequirementsProcessor implements PayloadProcessor {

    @Override
    @SuppressWarnings("unchecked")
    public Payload process(Chain chain, Payload payload) {
        Map<String, Object> req = payload.getHeader(Payload.REQUEST_DATA_MAP);

        Map<String, Object> requirements = (Map<String, Object>) req.get(InternalConstants.Request.REQUIREMENTS);
        if (requirements == null) {
            return chain.process(payload);
        }

        payload = payload.putHeader(Payload.REQUIREMENTS, requirements);

        return chain.process(payload);
    }
}
