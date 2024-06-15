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

import com.walmartlabs.concord.client2.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.walmartlabs.concord.client.ProjectTaskParams.Action;

public class ProjectTaskCommon {

    private static final Logger log = LoggerFactory.getLogger(ProjectTaskCommon.class);

    private final ProjectsApi api;
    private final String defaultOrg;

    public ProjectTaskCommon(ApiClient apiClient, String defaultOrg) {
        this.api = new ProjectsApi(apiClient);
        this.defaultOrg = defaultOrg;
    }

    public void execute(ProjectTaskParams in) throws Exception {
        Action action = in.action();
        switch (action) {
            case CREATE: {
                create(in);
                break;
            }
            default:
                throw new IllegalArgumentException("Unsupported action type: " + action);
        }
    }

    private void create(ProjectTaskParams in) throws Exception {
        ProjectEntry entry = new ProjectEntry();
        entry.setName(in.projectName());
        entry.setRepositories(in.repositories());

        ProjectOperationResponse resp = api.createOrUpdateProject(in.orgName(defaultOrg), entry);
        log.info("The project was created (or updated): {}", resp);
    }
}
