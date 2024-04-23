package com.walmartlabs.concord.agentoperator.scheduler;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import com.walmartlabs.concord.common.ConfigurationUtils;

import java.util.List;
import java.util.Map;

public class QueueSelector {

    public static QueueSelector parse(Map<String, Object> queueSelector) {
        String flavor;
        Object maybeFlavor = ConfigurationUtils.get(queueSelector, "agent", "flavor");
        if (maybeFlavor != null && !(maybeFlavor instanceof String)) {
            throw new IllegalArgumentException("Expected a string value as 'agent.flavor', got: " + maybeFlavor);
        }
        flavor = (String) maybeFlavor;

        List<String> queryParams;
        Object maybeQueryParams = ConfigurationUtils.get(queueSelector, "queryParams");
        if (maybeQueryParams != null) {
            if (!(maybeQueryParams instanceof List)) {
                throw new IllegalArgumentException("Expected a list value as 'queryParams', got: " + maybeQueryParams);
            }

            ((List<?>) maybeQueryParams).forEach(qp -> {
                if (!(qp instanceof String)) {
                    throw new IllegalArgumentException("Expected a string value as 'queryParams' item, got: " + qp);
                }
            });
        }
        //noinspection unchecked
        queryParams = (List<String>) maybeQueryParams;
        return new QueueSelector(flavor, queryParams);
    }

    private final String flavor;
    private final List<String> queryParams;

    private QueueSelector(String flavor, List<String> queryParams) {
        this.flavor = flavor;
        this.queryParams = queryParams;
    }

    /**
     * "Flavor" of the current agent. Translates to the "requirements.agent.flavor.eq"
     * query parameter when fetching the process queue.
     */
    public String getFlavor() {
        return flavor;
    }

    /**
     * Additional query parameters to be used when fetching the process queue. Appended
     * as-is to the query URL.
     */
    public List<String> getQueryParams() {
        return queryParams;
    }
}
