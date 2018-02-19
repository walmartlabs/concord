package com.walmartlabs.concord.client;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.server.api.org.project.ProjectEntry;
import com.walmartlabs.concord.server.api.org.project.ProjectOperationResponse;
import com.walmartlabs.concord.server.api.org.project.ProjectResource;
import com.walmartlabs.concord.server.api.org.project.RepositoryEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.client.Keys.ACTION_KEY;

@Named("project")
public class ProjectTask extends AbstractConcordTask {

    private static final Logger log = LoggerFactory.getLogger(ProjectTask.class);

    private static final String ORG_KEY = "org";
    private static final String NAME_KEY = "name";
    private static final String REPOSITORIES_KEY = "repositories";

    private final ObjectMapper mapper;

    public ProjectTask() {
        this.mapper = new ObjectMapper();
    }

    @Override
    public void execute(Context ctx) throws Exception {
        Action action = getAction(ctx);
        switch (action) {
            case CREATE: {
                create(ctx);
                break;
            }
            default:
                throw new IllegalArgumentException("Unsupported action type: " + action);
        }
    }

    @SuppressWarnings("unchecked")
    private void create(Context ctx) throws Exception {
        Map<String, Object> cfg = createCfg(ctx, ORG_KEY, NAME_KEY, REPOSITORIES_KEY);

        String orgName = getOrgName(ctx, cfg);
        Map<String, RepositoryEntry> repositories = toRepositories(cfg);

        ProjectEntry entry = new ProjectEntry(get(cfg, NAME_KEY), repositories);
        withClient(ctx, target -> {
            ProjectResource proxy = target.proxy(ProjectResource.class);
            ProjectOperationResponse resp = proxy.createOrUpdate(orgName, entry);
            log.info("The project was created (or updated): {}", resp);
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    private String getOrgName(Context ctx, Map<String, Object> cfg) {
        String s = (String) cfg.get(ORG_KEY);
        if (s == null) {
            Map<String, Object> projectInfo = (Map<String, Object>) ctx.getVariable(Constants.Request.PROJECT_INFO_KEY);
            if (projectInfo != null) {
                s = (String) projectInfo.get("orgName");
            }
        }

        if (s == null) {
            throw new IllegalArgumentException("'" + ORG_KEY + "' is required");
        }

        return s;
    }

    @SuppressWarnings("unchecked")
    private Map<String, RepositoryEntry> toRepositories(Map<String, Object> cfg) {
        Object repos = cfg.get(REPOSITORIES_KEY);
        if (repos instanceof Collection) {
            return toRepositories((Collection<Object>) repos);
        } else if (repos != null) {
            throw new IllegalArgumentException("'" + REPOSITORIES_KEY + "' must a list of repositories");
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, RepositoryEntry> toRepositories(Collection<Object> items) {
        Map<String, RepositoryEntry> result = new HashMap<>();

        for (Object i : items) {
            if (!(i instanceof Map)) {
                throw new IllegalArgumentException("Repository entry must be an object. Got: " + i);
            }

            Map<String, Object> r = (Map<String, Object>) i;

            String name = (String) r.get(NAME_KEY);
            if (name == null) {
                throw new IllegalArgumentException("Repository name is required");
            }

            result.put(name, parseRepository(r));
        }

        return result;
    }

    private static Action getAction(Context ctx) {
        Object v = ctx.getVariable(ACTION_KEY);
        if (v instanceof String) {
            String s = (String) v;
            return Action.valueOf(s.trim().toUpperCase());
        }
        throw new IllegalArgumentException("'" + ACTION_KEY + "' must be a string");
    }

    private RepositoryEntry parseRepository(Map<String, Object> r) {
        return mapper.convertValue(r, RepositoryEntry.class);
    }

    private enum Action {

        CREATE
    }
}
