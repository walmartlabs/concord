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
import com.walmartlabs.concord.server.org.project.ProjectEntry;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.pipelines.processors.RepositoryProcessor.RepositoryInfo;
import com.walmartlabs.concord.server.org.project.ProjectDao;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Named
public class ProjectInfoProcessor implements PayloadProcessor {

    private final ProjectDao projectDao;

    @Inject
    public ProjectInfoProcessor(ProjectDao projectDao) {
        this.projectDao = projectDao;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        Map<String, Object> m = createProjectInfo(payload);

        Map<String, Object> req = payload.getHeader(Payload.REQUEST_DATA_MAP);
        if (req == null) {
            req = new HashMap<>();
        }

        Map<String, Object> args = (Map<String, Object>) req.get(Constants.Request.ARGUMENTS_KEY);
        if (args == null) {
            args = new HashMap<>();
            req.put(Constants.Request.ARGUMENTS_KEY, args);
        }

        args.put(Constants.Request.PROJECT_INFO_KEY, m);

        return chain.process(payload.putHeader(Payload.REQUEST_DATA_MAP, req));
    }

    public Map<String, Object> createProjectInfo(Payload p) {
        UUID projectId = p.getHeader(Payload.PROJECT_ID);
        if (projectId == null) {
            Map<String, Object> m = new HashMap<>();
            m.put("orgId", OrganizationManager.DEFAULT_ORG_ID.toString());
            m.put("orgName", OrganizationManager.DEFAULT_ORG_NAME);
            return m;
        }

        ProjectEntry e = projectDao.get(projectId);
        if (e == null) {
            throw new ProcessException(p.getInstanceId(), "Project not found: " + projectId);
        }

        Map<String, Object> m = new HashMap<>();
        m.put("orgId", e.getOrgId());
        m.put("orgName", e.getOrgName());
        m.put("projectId", projectId);
        m.put("projectName", e.getName());

        RepositoryInfo r = p.getHeader(RepositoryProcessor.REPOSITORY_INFO_KEY);
        if (r != null) {
            m.put("repoId", r.getId());
            m.put("repoName", r.getName());
            m.put("repoUrl", r.getUrl());
            RepositoryProcessor.CommitInfo ci = r.getCommitInfo();
            if (ci != null) {
                m.put("repoCommitId", ci.getId());
                m.put("repoCommitAuthor", ci.getAuthor());
                m.put("repoCommitMessage", ci.getMessage());
            }
        }

        return m;
    }
}
