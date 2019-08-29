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
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.project.ProjectEntry;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.pipelines.processors.RepositoryProcessor.RepositoryInfo;

import javax.inject.Inject;
import javax.inject.Named;
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
    @SuppressWarnings("unchecked")
    public Payload process(Chain chain, Payload payload) {
        Map<String, Object> m = createProjectInfo(payload);

        Map<String, Object> cfg = payload.getHeader(Payload.CONFIGURATION);
        if (cfg == null) {
            cfg = new HashMap<>();
        }

        Map<String, Object> args = (Map<String, Object>) cfg.get(Constants.Request.ARGUMENTS_KEY);
        if (args == null) {
            args = new HashMap<>();
            cfg.put(Constants.Request.ARGUMENTS_KEY, args);
        }

        args.put(Constants.Request.PROJECT_INFO_KEY, m);

        return chain.process(payload.putHeader(Payload.CONFIGURATION, cfg));
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
            throw new ProcessException(p.getProcessKey(), "Project not found: " + projectId);
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
            m.put("repoBranch", r.getBranch());
            m.put("repoPath", r.getPath());
            RepositoryProcessor.CommitInfo ci = r.getCommitInfo();
            if (ci != null) {
                m.put("repoCommitId", ci.getId());
                m.put("repoCommitAuthor", ci.getAuthor());
                m.put("repoCommitMessage", escapeExpression(ci.getMessage()));
            }
        }

        return m;
    }

    private static String escapeExpression(String what) {
        if (what == null) {
            return null;
        }
        return what.replaceAll("\\$\\{", "\\\\\\${");
    }
}
