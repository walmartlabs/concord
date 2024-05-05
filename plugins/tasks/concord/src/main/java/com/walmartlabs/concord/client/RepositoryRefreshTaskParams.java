package com.walmartlabs.concord.client;

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

import com.walmartlabs.concord.runtime.v2.sdk.Variables;
import com.walmartlabs.concord.sdk.MapUtils;

import java.util.*;
import java.util.stream.Collectors;

public class RepositoryRefreshTaskParams {

    private static final String REPOSITORY_INFO = "repositoryInfo";
    private static final String EVENT = "event";

    private final Variables variables;

    public RepositoryRefreshTaskParams(Variables variables) {
        this.variables = variables;
    }

    public Map<String, Object> event() {
        return variables.getMap(EVENT, Collections.emptyMap());
    }

    public List<UUID> repositories() {
        Collection<Object> o = variables.getCollection(REPOSITORY_INFO, Collections.emptyList());
        return toUUIDs(o);
    }

    private List<UUID> toUUIDs(Collection<Object> items) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Invalid in parameters: no repository info");
        }
        return items.stream().map(v -> MapUtils.getUUID((Map<String, Object>)v, "repositoryId")).collect(Collectors.toList());
    }
}
