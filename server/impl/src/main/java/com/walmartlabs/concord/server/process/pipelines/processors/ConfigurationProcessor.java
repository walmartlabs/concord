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
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.process.loader.model.ProcessDefinition;
import com.walmartlabs.concord.process.loader.model.ProcessDefinitionUtils;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.org.OrganizationDao;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.project.ProjectEntry;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.ProcessKind;
import com.walmartlabs.concord.server.process.keys.AttachmentKey;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import com.walmartlabs.concord.server.process.pipelines.processors.cfg.ProcessConfigurationUtils;

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

    private final ProjectDao projectDao;
    private final OrganizationDao orgDao;
    private final ProcessLogManager logManager;

    @Inject
    public ConfigurationProcessor(ProjectDao projectDao, OrganizationDao orgDao, ProcessLogManager logManager) {
        this.projectDao = projectDao;
        this.orgDao = orgDao;
        this.logManager = logManager;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
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
        List<String> activeProfiles = ProcessConfigurationUtils.getActiveProfiles(payloadCfg, attachedCfg, workspaceCfg, projectCfg, orgCfg);
        payload = payload.putHeader(Payload.ACTIVE_PROFILES, activeProfiles);

        // consolidate out variables
        Set<String> outVars = new HashSet<>(ProcessConfigurationUtils.getOutVars(payloadCfg, attachedCfg, workspaceCfg));
        payload = payload.putHeader(Payload.OUT_EXPRESSIONS, outVars);

        // merged profile data
        Map<String, Object> profileCfg = getProfileCfg(payload, activeProfiles);

        // automatically provided variables
        Map<String, Object> providedCfg = ProcessConfigurationUtils.prepareProvidedCfg(payload, projectEntry);

        // configuration from the policy
        Map<String, Object> policyCfg = getPolicyCfg(payload);

        // create the resulting configuration
        Map<String, Object> m = ConfigurationUtils.deepMerge(policyDefCfg, orgCfg, projectCfg, profileCfg, workspaceCfg, attachedCfg, payloadCfg, providedCfg, policyCfg);
        m.put(Constants.Request.ACTIVE_PROFILES_KEY, activeProfiles);

        // handle handlers special params
        processHandlersConfiguration(payload, m);

        processDryRunModeConfiguration(payload, m);

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

        Map<String, Object> m = ProcessDefinitionUtils.getProfilesOverlayCfg(pd, activeProfiles);
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

    private static void processHandlersConfiguration(Payload payload, Map<String, Object> m) {
        ProcessKind processKind = payload.getHeader(Payload.PROCESS_KIND);
        if (processKind != ProcessKind.FAILURE_HANDLER &&
                processKind != ProcessKind.CANCEL_HANDLER &&
                processKind != ProcessKind.TIMEOUT_HANDLER) {
            return;
        }

        Object handlerTimeout = m.get(Constants.Request.HANDLER_PROCESS_TIMEOUT);
        if (handlerTimeout != null) {
            m.put(Constants.Request.PROCESS_TIMEOUT, handlerTimeout);
        }
    }

    private void processDryRunModeConfiguration(Payload payload, Map<String, Object> m) {
        Boolean dryRunMode = getBoolean(payload, m, Constants.Request.DRY_RUN_MODE_KEY);
        if (dryRunMode == null) {
            return;
        }
        m.put(Constants.Request.DRY_RUN_MODE_KEY, dryRunMode);
        if (dryRunMode) {
            logManager.info(payload.getProcessKey(), "Dry-run mode: true");
        }
    }

    private Boolean getBoolean(Payload payload, Map<String, Object> m, String key) {
        Object value = m.get(key);
        if (value == null) {
            return null;
        } else if (value instanceof Boolean booleanValue) {
            return booleanValue;
        } else if (value instanceof String stringValue) {
            if ("true".equalsIgnoreCase(stringValue)) {
                return true;
            } else if ("false".equalsIgnoreCase(stringValue)) {
                return false;
            }
        }
        throw new ProcessException(payload.getProcessKey(), String.format("Invalid '%s' mode value type. Expected 'true|false', got: '%s'", key, value), Status.BAD_REQUEST);
    }
}
