package com.walmartlabs.concord.server.policy;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.runtime.model.Trigger;
import com.walmartlabs.concord.server.org.OrganizationEntry;
import com.walmartlabs.concord.server.org.jsonstore.JsonStoreEntry;
import com.walmartlabs.concord.server.org.jsonstore.JsonStoreVisibility;
import com.walmartlabs.concord.server.org.project.ProjectEntry;
import com.walmartlabs.concord.server.org.project.RepositoryEntry;
import com.walmartlabs.concord.server.org.secret.SecretEntryV2;
import com.walmartlabs.concord.server.org.secret.SecretType;
import com.walmartlabs.concord.server.org.secret.SecretVisibility;
import com.walmartlabs.concord.server.user.UserEntry;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PolicyUtils {

    public static Map<String, Object> orgToMap(OrganizationEntry entry) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", entry.getId());
        m.put("name", entry.getName());
        if (entry.getMeta() != null) {
            m.put("meta", entry.getMeta());
        }
        if (entry.getCfg() != null) {
            m.put("cfg", entry.getCfg());
        }
        return m;
    }

    public static Map<String, Object> projectToMap(UUID orgId,
                                                   String orgName,
                                                   ProjectEntry entry) {

        Map<String, Object> m = new HashMap<>();
        m.put("id", entry.getId());
        m.put("name", entry.getName());
        m.put("orgId", orgId);
        m.put("orgName", orgName);
        if (entry.getVisibility() != null) {
            m.put("visibility", entry.getVisibility().name());
        }
        if (entry.getMeta() != null) {
            m.put("meta", entry.getMeta());
        }
        if (entry.getCfg() != null) {
            m.put("cfg", entry.getCfg());
        }
        return m;
    }

    public static Map<String, Object> repositoryToMap(ProjectEntry project,
                                                      RepositoryEntry repo,
                                                      SecretEntryV2 secret) {
        return repositoryToMap(project.getOrgId(), project.getOrgName(), project.getId(), project.getName(), repo, secret);
    }

    public static Map<String, Object> repositoryToMap(UUID orgId,
                                                      String orgName,
                                                      UUID projectId,
                                                      String projectName,
                                                      RepositoryEntry repo,
                                                      SecretEntryV2 secret) {

        Map<String, Object> m = new HashMap<>();
        m.put("orgId", orgId);
        m.put("orgName", orgName);
        m.put("projectId", projectId);
        m.put("projectName", projectName);
        m.put("name", repo.getName());
        m.put("url", repo.getUrl());
        m.put("branch", repo.getBranch());
        if (secret != null) {
            m.put("secret", secretToMap(secret.getOrgId(), secret.getName(), secret.getType(), secret.getVisibility(), secret.getStoreType()));
        }
        return m;
    }

    public static Map<String, Object> secretToMap(UUID orgId,
                                                  String secretName,
                                                  SecretType type,
                                                  SecretVisibility visibility,
                                                  String storeType) {

        Map<String, Object> m = new HashMap<>();
        m.put("name", secretName);
        m.put("orgId", orgId);
        if (type != null) {
            m.put("type", type.name());
        }
        if (visibility != null) {
            m.put("visibility", visibility.name());
        }
        if (storeType != null) {
            m.put("storeType", storeType);
        }
        return m;
    }

    public static Map<String, Object> triggerToMap(UUID orgId,
                                                   UUID projectId,
                                                   Trigger trigger) {

        Map<String, Object> m = new HashMap<>();
        m.put("eventSource", trigger.name());
        m.put("orgId", orgId);
        m.put("projectId", projectId);
        m.put("arguments", trigger.arguments() != null ? trigger.arguments() : Collections.emptyMap());
        m.put("params", trigger.conditions() != null ? trigger.conditions() : Collections.emptyMap());
        m.put("cfg", trigger.configuration() != null ? trigger.configuration() : Collections.emptyList());
        return m;
    }

    public static Map<String, Object> jsonStoreToMap(UUID orgId,
                                                     String storeName,
                                                     JsonStoreVisibility visibility,
                                                     UserEntry owner) {

        Map<String, Object> m = new HashMap<>();
        m.put("orgId", orgId);
        m.put("name", storeName);
        if (visibility != null) {
            m.put("visibility", visibility.name());
        }
        m.putAll(ownerToMap(owner));
        return m;
    }

    public static Map<String, Object> jsonStoreQueryToMap(OrganizationEntry org,
                                                          JsonStoreEntry store,
                                                          String queryName,
                                                          String query) {

        Map<String, Object> m = new HashMap<>();
        m.put("name", queryName);
        m.put("text", query);
        m.put("orgId", org.getId());
        m.put("orgName", org.getName());
        m.put("jsonStoreId", store.id());
        m.put("jsonStoreName", store.name());
        return m;
    }

    public static Map<String, Object> jsonStoreItemToMap(OrganizationEntry org,
                                                         JsonStoreEntry store,
                                                         String itemPath,
                                                         Object data) {

        Map<String, Object> attrs = new HashMap<>();
        attrs.put("orgId", org.getId());
        attrs.put("orgName", org.getName());
        attrs.put("jsonStoreId", store.id());
        attrs.put("jsonStoreName", store.name());
        attrs.put("path", itemPath);
        attrs.put("data", data);
        return attrs;
    }

    private static Map<String, Object> ownerToMap(UserEntry owner) {
        if (owner == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> result = new HashMap<>();
        result.put("ownerId", owner.getId());
        result.put("ownerName", owner.getName());
        if (owner.getDomain() != null) {
            result.put("ownerDomain", owner.getDomain());
        }
        result.put("ownerType", owner.getType().name());
        return result;
    }

    private PolicyUtils() {
    }
}
