package com.walmartlabs.concord.server.org.project;

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

import com.walmartlabs.concord.policyengine.CheckResult;
import com.walmartlabs.concord.policyengine.KvRule;
import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.server.policy.PolicyManager;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import com.walmartlabs.concord.server.security.UserPrincipal;

import javax.inject.Inject;
import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class KvManager {

    private static final String DEFAULT_POLICY_MESSAGE = "Maximum KV entries exceeded: current {0}, limit {1}";

    private final KvDao kvDao;
    private final ProjectDao projectDao;
    private final PolicyManager policyManager;

    @Inject
    public KvManager(KvDao kvDao, ProjectDao projectDao, PolicyManager policyManager) {
        this.kvDao = kvDao;
        this.projectDao = projectDao;
        this.policyManager = policyManager;
    }

    public void remove(UUID projectId, String key) {
        // TODO: assert project access
        kvDao.remove(projectId, key);
    }

    public void putString(UUID projectId, String key, String value) {
        // TODO: assert project access

        assertPolicy(projectId, key);

        kvDao.putString(projectId, key, value);
    }

    public String getString(UUID projectId, String key) {
        // TODO: assert project access
        return kvDao.getString(projectId, key);
    }

    public void putLong(UUID projectId, String key, long value) {
        // TODO: assert project access

        assertPolicy(projectId, key);

        kvDao.putLong(projectId, key, value);
    }

    public Long getLong(UUID projectId, String key) {
        // TODO: assert project access
        return kvDao.getLong(projectId, key);
    }

    public long inc(UUID projectId, String key) {
        // TODO: assert project access

        assertPolicy(projectId, key);

        return kvDao.inc(projectId, key);
    }

    private void assertPolicy(UUID projectId, String key) {
        UUID orgId = projectDao.getOrgId(projectId);
        if (orgId == null) {
            return;
        }

        PolicyEngine policyEngine = policyManager.get(orgId, projectId, UserPrincipal.assertCurrent().getUser().getId());
        if (policyEngine == null) {
            return;
        }

        CheckResult<KvRule, Integer> result = policyEngine.getKvPolicy().check(() -> kvDao.count(projectId), () -> kvDao.exists(projectId, key));
        if (!result.getDeny().isEmpty()) {
            throw new ValidationErrorsException("Found KV policy violations: " + buildErrorMessage(result.getDeny()));
        }
    }

    private static String buildErrorMessage(List<CheckResult.Item<KvRule, Integer>> errors) {
        StringBuilder sb = new StringBuilder();
        for (CheckResult.Item<KvRule, Integer> e : errors) {
            KvRule r = e.getRule();

            String msg = r.msg() != null ? r.msg() : DEFAULT_POLICY_MESSAGE;
            int actual = e.getEntity();
            int max = r.maxEntries();

            sb.append(MessageFormat.format(Objects.requireNonNull(msg), actual, max)).append(';');
        }
        return sb.toString();
    }
}
