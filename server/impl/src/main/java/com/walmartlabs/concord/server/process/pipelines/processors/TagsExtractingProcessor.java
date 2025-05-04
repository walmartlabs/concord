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
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.sdk.ProcessKey;

import java.util.*;
import java.util.stream.Collectors;

public class TagsExtractingProcessor implements PayloadProcessor {

    @Override
    public Payload process(Chain chain, Payload payload) {
        Map<String, Object> cfg = payload.getHeader(Payload.CONFIGURATION);

        Object v = cfg.get(Constants.Request.TAGS_KEY);
        if (v == null) {
            return chain.process(payload);
        }

        Set<String> tags = parse(payload.getProcessKey(), v);
        payload = payload.putHeader(Payload.PROCESS_TAGS, tags);

        return chain.process(payload);
    }

    @SuppressWarnings("rawtypes")
    private static Set<String> parse(ProcessKey pk, Object v) {
        if (v instanceof String) {
            String[] as = ((String) v).split(",");
            return trim(as);
        } else if (v instanceof String[]) {
            String[] as = (String[]) v;
            return trim(as);
        } else if (v instanceof Collection) {
            Set<String> result = new HashSet<>();

            Collection c = (Collection) v;
            for (Object o : c) {
                if (!(o instanceof String)) {
                    throw new ProcessException(pk, "Process tag must be a string value: " + o);
                }

                result.add((String) o);
            }

            return result.stream().map(String::trim).collect(Collectors.toSet());
        } else {
            throw new ProcessException(pk, "Process tags must be an array of string values: " + v);
        }
    }

    private static Set<String> trim(String[] as) {
        return Arrays.stream(as).map(String::trim).collect(Collectors.toSet());
    }
}
