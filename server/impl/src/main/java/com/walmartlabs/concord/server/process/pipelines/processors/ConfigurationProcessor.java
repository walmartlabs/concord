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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.runtime.loader.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.loader.model.ProcessDefinitionUtils;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.cfg.DefaultProcessConfiguration;
import com.walmartlabs.concord.server.org.OrganizationDao;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.project.ProjectEntry;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.keys.AttachmentKey;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Responsible for preparing the process' {@code configuration} object.
 */
@Named
public class ConfigurationProcessor implements PayloadProcessor {

    public static final AttachmentKey REQUEST_ATTACHMENT_KEY = AttachmentKey.register("request");
    private static final List<String> DEFAULT_PROFILES = Collections.singletonList("default");

    private final ProjectDao projectDao;
    private final OrganizationDao orgDao;
    private final DefaultProcessConfiguration defaultCfg;

    @Inject
    public ConfigurationProcessor(ProjectDao projectDao, OrganizationDao orgDao, DefaultProcessConfiguration defaultCfg) {
        this.projectDao = projectDao;
        this.orgDao = orgDao;
        this.defaultCfg = defaultCfg;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        // system-level default configuration
        Map<String, Object> defCfg = defaultCfg.getCfg();

        // default configuration from policy
        Map<String, Object> policyDefCfg = getDefaultCfgFromPolicy(payload);

        // org configuration
        Map<String, Object> orgCfg = getOrgCfg(payload);

        ProjectEntry projectEntry = getProject(payload);

        // project configuration
        Map<String, Object> projectCfg = getProjectCfg(projectEntry);

        // _main.json file in the workspace
        Map<String, Object> workspaceCfg = getWorkspaceCfg(payload);

        // attached to the request JSON file
        Map<String, Object> attachedCfg = getAttachedCfg(payload);

        // existing configuration values from the payload
        Map<String, Object> payloadCfg = payload.getHeader(Payload.CONFIGURATION, Collections.emptyMap());

        // determine the active profile names
        List<String> activeProfiles = getActiveProfiles(ImmutableList.of(payloadCfg, attachedCfg, workspaceCfg, projectCfg, orgCfg));
        payload = payload.putHeader(Payload.ACTIVE_PROFILES, activeProfiles);

        // merged profile data
        Map<String, Object> profileCfg = getProfileCfg(payload, activeProfiles);

        // automatically provided variables
        Map<String, Object> providedCfg = getProvidedCfg(payload, projectEntry);

        // configuration from the policy
        Map<String, Object> policyCfg = getPolicyCfg(payload);

        // create the resulting configuration
        Map<String, Object> m = ConfigurationUtils.deepMerge(defCfg, policyDefCfg, orgCfg, projectCfg, profileCfg, workspaceCfg, attachedCfg, payloadCfg, providedCfg, policyCfg);
        m.put(Constants.Request.ACTIVE_PROFILES_KEY, activeProfiles);

        payload = payload.putHeader(Payload.CONFIGURATION, m);

        return chain.process(payload);
    }

    private ProjectEntry getProject(Payload payload) {
        UUID projectId = payload.getHeader(Payload.PROJECT_ID);
        if (projectId == null) {
            return null;
        }

        ProjectEntry e = projectDao.get(projectId);
        if (e == null) {
            throw new ProcessException(payload.getProcessKey(), "Project not found: " + projectId, Status.BAD_REQUEST);
        }

        return e;
    }

    private Map<String, Object> getProjectCfg(ProjectEntry projectEntry) {
        if (projectEntry == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> m = projectEntry.getCfg();
        return m != null ? m : Collections.emptyMap();
    }

    private Map<String, Object> getOrgCfg(Payload payload) {
        UUID orgId = payload.getHeader(Payload.ORGANIZATION_ID);
        if (orgId == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> m = orgDao.getConfiguration(orgId);
        return m != null ? m : Collections.emptyMap();
    }

    private Map<String, Object> getDefaultCfgFromPolicy(Payload payload) {
        PolicyEngine policy = payload.getHeader(Payload.POLICY);

        if (policy == null) {
            return Collections.emptyMap();
        }

        return policy.getDefaultProcessCfgPolicy().get();
    }

    private Map<String, Object> getPolicyCfg(Payload payload) {
        PolicyEngine policy = payload.getHeader(Payload.POLICY);

        if (policy == null) {
            return Collections.emptyMap();
        }

        return policy.getProcessCfgPolicy().get();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getWorkspaceCfg(Payload payload) {
        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);
        Path src = workspace.resolve(Constants.Files.CONFIGURATION_FILE_NAME);
        if (!Files.exists(src)) {
            return Collections.emptyMap();
        }

        try (InputStream in = Files.newInputStream(src)) {
            ObjectMapper om = new ObjectMapper();
            return om.readValue(in, Map.class);
        } catch (IOException e) {
            throw new ProcessException(payload.getProcessKey(), "Invalid request data format", e, Status.BAD_REQUEST);
        }
    }

    private static Map<String, Object> getProfileCfg(Payload payload, List<String> activeProfiles) {
        if (activeProfiles == null) {
            activeProfiles = Collections.emptyList();
        }

        ProcessDefinition pd = payload.getHeader(Payload.PROJECT_DEFINITION);
        if (pd == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> m = ProcessDefinitionUtils.getVariables(pd, activeProfiles);
        return m != null ? m : Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getAttachedCfg(Payload payload) {
        Path p = payload.getAttachment(REQUEST_ATTACHMENT_KEY);
        if (p == null) {
            return Collections.emptyMap();
        }

        try (InputStream in = Files.newInputStream(p)) {
            ObjectMapper om = new ObjectMapper();
            return om.readValue(in, Map.class);
        } catch (IOException e) {
            throw new ProcessException(payload.getProcessKey(), "Invalid request data format", e, Status.BAD_REQUEST);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> getActiveProfiles(List<Map<String, Object>> mm) {
        for (Map<String, Object> m : mm) {
            Object o = m.get(Constants.Request.ACTIVE_PROFILES_KEY);
            if (o == null) {
                continue;
            }

            if (o instanceof String) {
                String s = (String) o;
                String[] as = s.trim().split(",");
                return Arrays.asList(as);
            } else if (o instanceof List) {
                return (List<String>) o;
            } else {
                throw new IllegalArgumentException("Invalid '" + Constants.Request.ACTIVE_PROFILES_KEY +
                        "' value. Expected a JSON array or a comma-delimited list of profiles, got: " + o);
            }
        }

        return DEFAULT_PROFILES;
    }

    /**
     * Creates a process {@code configuration} object with variables that
     * are automatically provided by Concord for any process.
     * <p/>
     * Created values should be applied last (or before the configuration from a policy)
     * to avoid users from changing them via request parameters.
     */
    public static Map<String, Object> getProvidedCfg(Payload payload, ProjectEntry projectEntry) {
        Map<String, Object> args = new HashMap<>();

        // TODO verify that all "provided" variables are set here and only here

        UUID parentInstanceId = payload.getHeader(Payload.PARENT_INSTANCE_ID);
        if (parentInstanceId != null) {
            args.put(Constants.Request.PARENT_INSTANCE_ID_KEY, parentInstanceId.toString());
        }

        args.put(Constants.Request.PROCESS_INFO_KEY, createProcessInfo(payload));
        args.put(Constants.Request.PROJECT_INFO_KEY, createProjectInfo(payload, projectEntry));

        return Collections.singletonMap(Constants.Request.ARGUMENTS_KEY, args);
    }

    private static Map<String, Object> createProjectInfo(Payload payload, ProjectEntry projectEntry) {
        if (projectEntry == null) {
            Map<String, Object> m = new HashMap<>();
            m.put("orgId", OrganizationManager.DEFAULT_ORG_ID.toString());
            m.put("orgName", OrganizationManager.DEFAULT_ORG_NAME);
            return m;
        }

        Map<String, Object> m = new HashMap<>();
        m.put("orgId", projectEntry.getOrgId());
        m.put("orgName", projectEntry.getOrgName());
        m.put("projectId", projectEntry.getId());
        m.put("projectName", projectEntry.getName());

        RepositoryProcessor.RepositoryInfo r = payload.getHeader(RepositoryProcessor.REPOSITORY_INFO_KEY);
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

    private static Map<String, Object> createProcessInfo(Payload payload) {
        Map<String, Object> processInfo = new HashMap<>();

        String token = payload.getHeader(Payload.SESSION_TOKEN);
        if (token != null) {
            // TODO rename to sessionToken?
            processInfo.put("sessionKey", token);
        }

        Collection<String> activeProfiles = payload.getHeader(Payload.ACTIVE_PROFILES);
        if (activeProfiles == null) {
            activeProfiles = Collections.emptyList();
        }
        processInfo.put("activeProfiles", activeProfiles);

        return processInfo;
    }

    private static String escapeExpression(String what) {
        if (what == null) {
            return null;
        }
        return what.replaceAll("\\$\\{", "\\\\\\${");
    }
}
