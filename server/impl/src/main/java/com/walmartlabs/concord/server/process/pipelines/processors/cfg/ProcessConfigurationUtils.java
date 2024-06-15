package com.walmartlabs.concord.server.process.pipelines.processors.cfg;

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

import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.project.ProjectEntry;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.pipelines.processors.RepositoryProcessor;

import java.util.*;
import java.util.stream.Collectors;

public final class ProcessConfigurationUtils {

    private static final List<String> DEFAULT_PROFILES = Collections.singletonList("default");

    /**
     * Creates a process {@code configuration} object with variables that
     * are automatically provided by Concord for any process.
     * <p/>
     * Created values should be applied last (or before the configuration from a policy)
     * to avoid users from changing them via request parameters.
     */
    public static Map<String, Object> prepareProvidedCfg(Payload payload, ProjectEntry projectEntry) {
        Map<String, Object> args = new HashMap<>();

        // TODO verify that all "provided" variables are set here and only here

        UUID parentInstanceId = payload.getHeader(Payload.PARENT_INSTANCE_ID);
        if (parentInstanceId != null) {
            args.put(Constants.Request.PARENT_INSTANCE_ID_KEY, parentInstanceId.toString());
        }

        Map<String, Object> cfg = new HashMap<>();
        cfg.put(Constants.Request.ARGUMENTS_KEY, args);

        cfg.put(Constants.Request.PROCESS_INFO_KEY, createProcessInfo(payload));
        cfg.put(Constants.Request.PROJECT_INFO_KEY, createProjectInfo(payload, projectEntry));

        return cfg;
    }

    @SafeVarargs
    @SuppressWarnings("unchecked")
    public static List<String> getActiveProfiles(Map<String, Object> ... mm) {
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
                return assertListType("activeProfiles", removeNulls((List<String>) o), String.class);
            } else {
                throw new IllegalArgumentException("Invalid '" + Constants.Request.ACTIVE_PROFILES_KEY +
                        "' value. Expected a JSON array or a comma-delimited list of profiles, got: " + o);
            }
        }

        return DEFAULT_PROFILES;
    }

    @SafeVarargs
    @SuppressWarnings("unchecked")
    public static Set<String> getOutVars(Map<String, Object> ... mm) {
        Set<String> outVars = new HashSet<>();

        for (Map<String, Object> m : mm) {
            Object o = m.get(Constants.Request.OUT_EXPRESSIONS_KEY);

            if (o == null) {
                continue;
            }

            if (o instanceof List) {
                outVars.addAll(assertListType("out", removeNulls((List<String>) o), String.class));
            } else if (o instanceof String) {
                outVars.addAll(Arrays.asList(((String) o).split(",")));
            } else {
                throw new IllegalArgumentException("Invalid '" + Constants.Request.OUT_EXPRESSIONS_KEY +
                        "' value. Expected JSON array, got: " + o);
            }
        }

        return outVars;
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
        Map<String, Object> m = new HashMap<>();

        String token = payload.getHeader(Payload.SESSION_TOKEN);
        if (token != null) {
            // TODO rename to sessionToken? or deprecate in favor of the top-level "sessionToken" value
            m.put("sessionKey", token);
        }

        List<String> activeProfiles = payload.getHeader(Payload.ACTIVE_PROFILES);
        if (activeProfiles == null) {
            activeProfiles = DEFAULT_PROFILES;
        }
        m.put("activeProfiles", activeProfiles);

        String sessionToken = payload.getHeader(Payload.SESSION_TOKEN);
        if (sessionToken != null) {
            m.put(Constants.Request.SESSION_TOKEN_KEY, sessionToken);
        }

        return m;
    }

    private static String escapeExpression(String what) {
        if (what == null) {
            return null;
        }
        return what.replaceAll("\\$\\{", "\\\\\\${");
    }

    private static <T> List<T> removeNulls(List<T> c) {
        if (c == null) {
            return Collections.emptyList();
        }

        return c.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static <T> List<T> assertListType(String itemsName, List<T> items, Class<T> type) {
        if (items == null) {
            return Collections.emptyList();
        }

        for (T i : items) {
            if (i == null) {
                throw new IllegalArgumentException("Invalid " + itemsName + " values " + items + ": expected 'String' type, got 'null'");
            }

            if (!(type.isAssignableFrom(i.getClass()))) {
                throw new IllegalArgumentException("Invalid " + itemsName + " values " + items + ": expected 'String' type, got '" + i.getClass() + "'");
            }
        }
        return items;
    }

    private ProcessConfigurationUtils() {
    }
}
