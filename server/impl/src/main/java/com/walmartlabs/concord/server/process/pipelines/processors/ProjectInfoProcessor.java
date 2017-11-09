package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.api.project.ProjectEntry;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.pipelines.processors.RepositoryProcessor.RepositoryInfo;
import com.walmartlabs.concord.server.project.ProjectDao;

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
        Map<String, Object> m = createProcessInfo(payload);

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

    public Map<String, Object> createProcessInfo(Payload p) {
        UUID projectId = p.getHeader(Payload.PROJECT_ID);
        if (projectId == null) {
            return Collections.emptyMap();
        }

        ProjectEntry e = projectDao.get(projectId);
        if (e == null) {
            throw new ProcessException(p.getInstanceId(), "Project not found: " + projectId);
        }

        Map<String, Object> m = new HashMap<>();
        m.put("teamId", e.getTeamId());
        m.put("teamName", e.getTeamName());
        m.put("projectId", projectId);
        m.put("projectName", e.getName());

        RepositoryInfo r = p.getHeader(RepositoryProcessor.REPOSITORY_INFO_KEY);
        if (r != null) {
            m.put("repoId", r.getId());
            m.put("repoName", r.getName());
            m.put("repoUrl", r.getUrl());
        }

        return m;
    }
}
