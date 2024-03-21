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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.client2.RepositoryEntry;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;
import com.walmartlabs.concord.sdk.MapUtils;

import java.util.*;

public class ProjectTaskParams {

    private static final String ORG_KEY = "org";
    private static final String NAME_KEY = "name";
    private static final String REPOSITORIES_KEY = "repositories";

    private final ObjectMapper mapper;
    private final Variables variables;

    public ProjectTaskParams(Variables variables) {
        this.mapper = new ObjectMapper();
        this.variables = variables;
    }

    public Action action() {
        String v = variables.assertString(Keys.ACTION_KEY);
        try {
            return Action.valueOf(v.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Unknown action: '" + v + "'. Available actions: " + Arrays.toString(Action.values()));
        }
    }

    public String orgName(String defaultOrg) {
        String org = variables.getString(ORG_KEY);
        if (org != null) {
            return org;
        }

        if (defaultOrg != null) {
            return defaultOrg;
        }

        throw new IllegalArgumentException("'" + ORG_KEY + "' is required");
    }

    public String projectName() {
        return variables.getString(NAME_KEY, null);
    }

    public Map<String, RepositoryEntry> repositories() {
        Collection<Object> o = variables.getCollection(REPOSITORIES_KEY, Collections.emptyList());
        return toRepositories(o);
    }

    @SuppressWarnings("unchecked")
    private Map<String, RepositoryEntry> toRepositories(Collection<Object> items) {
        if (items.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, RepositoryEntry> result = new HashMap<>();
        for (Object i : items) {
            if (!(i instanceof Map)) {
                throw new IllegalArgumentException("Repository entry must be an object. Got: " + i);
            }

            Map<String, Object> r = (Map<String, Object>) i;

            String name = MapUtils.getString(r, NAME_KEY);
            if (name == null) {
                throw new IllegalArgumentException("Repository name is required");
            }

            result.put(name, parseRepository(r));
        }

        return result;
    }

    private RepositoryEntry parseRepository(Map<String, Object> r) {
        return mapper.convertValue(r, RepositoryEntry.class);
    }


    public enum Action {

        CREATE
    }
}
