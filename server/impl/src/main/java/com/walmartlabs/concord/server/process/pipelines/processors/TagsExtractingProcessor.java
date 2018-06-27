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
import com.walmartlabs.concord.server.process.ProcessException;

import javax.inject.Named;
import java.util.*;

@Named
public class TagsExtractingProcessor implements PayloadProcessor {

    @Override
    @SuppressWarnings("unchecked")
    public Payload process(Chain chain, Payload payload) {
        Map<String, Object> req = payload.getHeader(Payload.REQUEST_DATA_MAP);

        Object v = req.get(InternalConstants.Request.TAGS_KEY);
        if (v == null) {
            return chain.process(payload);
        }

        Set<String> tags;

        if (v instanceof String[]) {
            String[] as = (String[]) v;
            tags = new HashSet<>(as.length);
            Collections.addAll(tags, as);
        } else if (v instanceof Collection) {
            Collection c = (Collection) v;
            tags = new HashSet<>(c.size());

            for (Object o : c) {
                if (o instanceof String) {
                    tags.add((String) o);
                } else {
                    throw new ProcessException(payload.getInstanceId(), "Process tag must be a string value: " + o);
                }
            }
        } else {
            throw new ProcessException(payload.getInstanceId(), "Process tags must be an array of string values: " + v);
        }

        payload = payload.putHeader(Payload.PROCESS_TAGS, tags);

        return chain.process(payload);
    }
}
