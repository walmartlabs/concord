package com.walmartlabs.concord.server.org.policy;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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
import com.google.common.base.Throwables;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.api.org.policy.PolicyEntry;
import org.jooq.*;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.Tables.POLICIES;
import static com.walmartlabs.concord.server.jooq.Tables.POLICY_LINKS;

@Named
public class PolicyDao extends AbstractDao {

    private final ObjectMapper objectMapper;

    @Inject
    public PolicyDao(Configuration cfg) {
        super(cfg);

        this.objectMapper = new ObjectMapper();
    }

    public PolicyEntry get(UUID orgId, UUID projectId) {
        try (DSLContext tx = DSL.using(cfg)) {

            SelectOnConditionStep<Record5<UUID, String, String, UUID, UUID>> q =
                    tx.select(POLICIES.POLICY_ID,
                            POLICIES.POLICY_NAME,
                            POLICIES.RULES.cast(String.class),
                            POLICY_LINKS.ORG_ID,
                            POLICY_LINKS.PROJECT_ID)
                            .from(POLICY_LINKS)
                            .leftOuterJoin(POLICIES).on(POLICY_LINKS.POLICY_ID.eq(POLICIES.POLICY_ID));

            // system policy
            Condition c = (POLICY_LINKS.ORG_ID.isNull().and(POLICY_LINKS.PROJECT_ID.isNull()));

            if (projectId != null) {
                c = c.or(POLICY_LINKS.PROJECT_ID.eq(projectId));
            }

            if (orgId != null) {
                c = c.or(POLICY_LINKS.ORG_ID.eq(orgId).and(POLICY_LINKS.PROJECT_ID.isNull()));
            }

            q.where(c);

            return findPolicyEntry(q.fetch(this::toEntry));
        }
    }

    private PolicyEntry findPolicyEntry(List<PolicyRule> rules) {
        PolicyRule prj = rules.stream().filter(r -> r.prjId != null).findFirst().orElse(null);
        if (prj != null) {
            return toEntry(prj);
        }
        PolicyRule org = rules.stream().filter(r -> r.orgId != null && r.prjId == null).findFirst().orElse(null);
        if (org != null) {
            return toEntry(org);
        }
        PolicyRule system = rules.stream().filter(r -> r.orgId == null && r.prjId == null).findFirst().orElse(null);
        if (system != null) {
            return toEntry(system);
        }
        return null;
    }

    private static PolicyEntry toEntry(PolicyRule r) {
        return new PolicyEntry(r.policyId, r.policyName, r.rules);
    }

    private PolicyRule toEntry(Record5<UUID, String, String, UUID, UUID> r) {
        return new PolicyRule(
                r.get(POLICY_LINKS.ORG_ID),
                r.get(POLICY_LINKS.PROJECT_ID),
                r.get(POLICIES.POLICY_ID),
                r.get(POLICIES.POLICY_NAME),
                deserialize(r.value3()));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deserialize(String ab) {
        if (ab == null) {
            return null;
        }

        try {
            return objectMapper.readValue(ab, Map.class);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private class PolicyRule {

        private final UUID orgId;
        private final UUID prjId;
        private final UUID policyId;
        private final String policyName;
        private final Map<String, Object> rules;

        private PolicyRule(UUID orgId, UUID prjId, UUID policyId, String policyName, Map<String, Object> rules) {
            this.orgId = orgId;
            this.prjId = prjId;
            this.policyId = policyId;
            this.policyName = policyName;
            this.rules = rules;
        }
    }
}
