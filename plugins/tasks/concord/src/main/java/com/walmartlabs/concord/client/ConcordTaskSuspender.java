package com.walmartlabs.concord.client;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.sdk.MapUtils;

import java.io.Serializable;
import java.util.*;

public interface ConcordTaskSuspender {

    String suspend(boolean resumeFromSameStep, ResumePayload payload);

    class ResumePayload implements Serializable {

        private final String baseUrl;
        private final String apiKey;
        private final boolean collectOutVars;
        private final List<UUID> jobs;
        private final boolean ignoreFailures;

        public ResumePayload(String baseUrl, String apiKey, boolean collectOutVars, List<UUID> jobs, boolean ignoreFailures) {
            this.baseUrl = baseUrl;
            this.apiKey = apiKey;
            this.collectOutVars = collectOutVars;
            this.jobs = jobs;
            this.ignoreFailures = ignoreFailures;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        public ResumePayload(Map items) {
            this(MapUtils.getString(items, "baseUrl"),
                    MapUtils.getString(items, "apiKey"),
                    MapUtils.getBoolean(items, "collectOutVars", false),
                    new ArrayList<>(MapUtils.getList(items, "jobs", Collections.emptyList())),
                    MapUtils.getBoolean(items, "ignoreFailures", false));
        }

        public Map<String, Serializable> asMap() {
            Map<String, Serializable> result = new HashMap<>();
            result.put("baseUrl", baseUrl);
            result.put("apiKey", apiKey);
            result.put("collectOutVars", collectOutVars);
            if (jobs != null) {
                result.put("jobs", new ArrayList<>(jobs));
            }
            result.put("ignoreFailures", ignoreFailures);
            return result;
        }

        public String baseUrl() {
            return baseUrl;
        }

        public String apiKey() {
            return apiKey;
        }

        public boolean collectOutVars() {
            return collectOutVars;
        }

        public List<UUID> jobs() {
            return jobs;
        }

        public boolean ignoreFailures() {
            return ignoreFailures;
        }
    }
}
