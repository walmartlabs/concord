package com.walmartlabs.concord.server.events.github;

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

import com.walmartlabs.concord.server.cfg.GithubConfiguration;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.project.ProjectEntry;
import com.walmartlabs.concord.server.org.triggers.TriggerEntry;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.server.events.github.Constants.*;

@Named
@Singleton
public class GithubTriggerDefinitionEnricher {

    private final ProjectDao projectDao;
    private final GithubConfiguration cfg;

    @Inject
    public GithubTriggerDefinitionEnricher(ProjectDao projectDao, GithubConfiguration cfg) {
        this.projectDao = projectDao;
        this.cfg = cfg;
    }

    public TriggerEntry enrich(TriggerEntry entry) {
        // note that the resulting conditions must be compatible with the system trigger definitions
        // see com/walmartlabs/concord/server/org/triggers/concord.yml

        Map<String, Object> conditions = new HashMap<>();

        // add default conditions from the cfg file
        if (cfg.getDefaultFilter() != null) {
            conditions.putAll(cfg.getDefaultFilter());
        }

        // add the trigger definition's conditions
        if (entry.getConditions() != null) {
            conditions.putAll(entry.getConditions());
        }

        // compute the additional filters
        conditions.computeIfAbsent(ORG_NAME_KEY, k -> {
            ProjectEntry e = projectDao.get(entry.getProjectId());
            if (e == null) {
                return null;
            }
            return e.getOrgName();
        });
        conditions.computeIfAbsent(PROJECT_NAME_KEY, k -> entry.getProjectName());
        conditions.computeIfAbsent(REPO_NAME_KEY, k -> entry.getRepositoryName());

        // TODO remove once the documentation and existing triggers are updated
        conditions.putIfAbsent(TYPE_KEY, DEFAULT_EVENT_TYPE);

        return new TriggerEntry(entry.getId(),
                entry.getOrgId(),
                entry.getOrgName(),
                entry.getProjectId(),
                entry.getProjectName(),
                entry.getRepositoryId(),
                entry.getRepositoryName(),
                entry.getEventSource(),
                entry.getActiveProfiles(),
                entry.getArguments(),
                conditions,
                entry.getCfg());
    }
}
