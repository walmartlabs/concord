package com.walmartlabs.concord.server.org.policy;

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
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.jooq.tables.records.PolicyLinksRecord;
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
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.value;

@Named
public class PolicyDao extends AbstractDao {

    private final ObjectMapper objectMapper;

    @Inject
    public PolicyDao(@Named("app") Configuration cfg) {
        super(cfg);

        this.objectMapper = new ObjectMapper();
    }

    public UUID getId(String name) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(POLICIES.POLICY_ID)
                    .from(POLICIES)
                    .where(POLICIES.POLICY_NAME.eq(name))
                    .fetchOne(POLICIES.POLICY_ID);
        }
    }

    public PolicyEntry get(UUID policyId) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(POLICIES.POLICY_ID,
                    POLICIES.POLICY_NAME,
                    POLICIES.RULES.cast(String.class))
                    .from(POLICIES)
                    .where(POLICIES.POLICY_ID.eq(policyId))
                    .fetchOne(this::toEntry);
        }
    }

    public PolicyEntry getLinked(UUID orgId, UUID projectId, UUID userId) {
        try (DSLContext tx = DSL.using(cfg)) {
            return getLinked(tx, orgId, projectId, userId);
        }
    }

    public PolicyEntry getLinked(DSLContext tx, UUID orgId, UUID projectId, UUID userId) {
        SelectOnConditionStep<Record6<UUID, String, String, UUID, UUID, UUID>> q =
                tx.select(POLICIES.POLICY_ID,
                        POLICIES.POLICY_NAME,
                        POLICIES.RULES.cast(String.class),
                        POLICY_LINKS.ORG_ID,
                        POLICY_LINKS.PROJECT_ID,
                        POLICY_LINKS.USER_ID)
                        .from(POLICY_LINKS)
                        .leftOuterJoin(POLICIES).on(POLICY_LINKS.POLICY_ID.eq(POLICIES.POLICY_ID));

        // system policy
        Condition c = ((POLICY_LINKS.ORG_ID.isNull().and(POLICY_LINKS.PROJECT_ID.isNull()).and(POLICY_LINKS.USER_ID.isNull())));

        if (projectId != null) {
            c = c.or((POLICY_LINKS.PROJECT_ID.eq(projectId).and(POLICY_LINKS.USER_ID.isNull())));
        }

        if (orgId != null) {
            c = c.or((POLICY_LINKS.ORG_ID.eq(orgId).and(POLICY_LINKS.PROJECT_ID.isNull()).and(POLICY_LINKS.USER_ID.isNull())));
        }

        if (userId != null) {
            if (projectId != null) {
                c = c.or((POLICY_LINKS.USER_ID.eq(userId)).and(POLICY_LINKS.PROJECT_ID.eq(projectId)));
            }

            if (orgId != null) {
                c = c.or((POLICY_LINKS.USER_ID.eq(userId)).and(POLICY_LINKS.ORG_ID.eq(orgId)).and(POLICY_LINKS.PROJECT_ID.isNull()));
            }

            c = c.or((POLICY_LINKS.USER_ID.eq(userId)).and(POLICY_LINKS.ORG_ID.isNull()).and(POLICY_LINKS.PROJECT_ID.isNull()));
        }

        q.where(c);

        return findPolicyEntry(q.fetch(this::toRule));
    }

    public UUID insert(String name, Map<String, Object> rules) {
        return txResult(tx -> tx.insertInto(POLICIES)
                .columns(POLICIES.POLICY_NAME, POLICIES.RULES)
                .values(value(name), field("?::jsonb", serialize(rules)))
                .returning(POLICIES.POLICY_ID)
                .fetchOne()
                .getPolicyId());
    }

    public void update(UUID policyId, String name, Map<String, Object> rules) {
        tx(tx -> tx.update(POLICIES)
                .set(POLICIES.POLICY_NAME, name)
                .set(POLICIES.RULES, field("?::jsonb", String.class, serialize(rules)))
                .where(POLICIES.POLICY_ID.eq(policyId))
                .execute());
    }

    public void delete(UUID policyId) {
        tx(tx -> tx.deleteFrom(POLICIES)
                .where(POLICIES.POLICY_ID.eq(policyId))
                .execute());
    }

    public void link(UUID policyId, UUID orgId, UUID projectId, UUID userId) {
        tx(tx -> tx.insertInto(POLICY_LINKS)
                .columns(POLICY_LINKS.POLICY_ID, POLICY_LINKS.ORG_ID, POLICY_LINKS.PROJECT_ID, POLICY_LINKS.USER_ID)
                .values(policyId, orgId, projectId, userId)
                .execute());
    }

    public void unlink(UUID policyId, UUID orgId, UUID projectId, UUID userId) {
        tx(tx -> {
            DeleteConditionStep<PolicyLinksRecord> q = tx.deleteFrom(POLICY_LINKS)
                    .where(POLICY_LINKS.POLICY_ID.eq(policyId));

            if (projectId != null) {
                q.and(POLICY_LINKS.PROJECT_ID.eq(projectId));
            } else if (orgId != null) {
                q.and(POLICY_LINKS.ORG_ID.eq(orgId));
            }

            if (userId != null) {
                q.and(POLICY_LINKS.USER_ID.eq(userId));
            }

            q.execute();
        });
    }

    public List<PolicyEntry> list() {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(POLICIES.POLICY_ID,
                    POLICIES.POLICY_NAME,
                    POLICIES.RULES.cast(String.class))
                    .from(POLICIES)
                    .fetch(this::toEntry);
        }
    }

    private PolicyEntry findPolicyEntry(List<PolicyRule> rules) {
        PolicyRule userRule = findUserLevelRule(rules);
        if (userRule != null) {
            return toEntry(userRule);
        }

        PolicyRule prjRule = rules.stream().filter(r -> r.prjId != null).findFirst().orElse(null);
        if (prjRule != null) {
            return toEntry(prjRule);
        }

        PolicyRule orgRule = rules.stream().filter(r -> r.orgId != null && r.prjId == null).findFirst().orElse(null);
        if (orgRule != null) {
            return toEntry(orgRule);
        }

        PolicyRule systemRule = rules.stream().filter(r -> r.orgId == null && r.prjId == null).findFirst().orElse(null);
        if (systemRule != null) {
            return toEntry(systemRule);
        }

        return null;
    }

    private PolicyRule findUserLevelRule(List<PolicyRule> rules) {
        PolicyRule userPrjRule = rules.stream().filter(r -> r.userId != null && r.prjId != null).findFirst().orElse(null);
        if (userPrjRule != null) {
            return userPrjRule;
        }

        PolicyRule userOrgRule = rules.stream().filter(r -> r.userId != null && r.orgId != null && r.prjId == null).findFirst().orElse(null);
        if (userOrgRule != null) {
            return userOrgRule;
        }

        PolicyRule userRule = rules.stream().filter(r -> r.userId != null && r.orgId == null && r.prjId == null).findFirst().orElse(null);
        if (userRule != null) {
            return userRule;
        }

        return null;
    }

    private static PolicyEntry toEntry(PolicyRule r) {
        return new PolicyEntry(r.policyId, r.policyName, r.rules);
    }

    private PolicyEntry toEntry(Record3<UUID, String, String> r) {
        return new PolicyEntry(r.get(POLICIES.POLICY_ID),
                r.get(POLICIES.POLICY_NAME),
                deserialize(r.value3()));
    }

    private PolicyRule toRule(Record6<UUID, String, String, UUID, UUID, UUID> r) {
        return new PolicyRule(
                r.get(POLICY_LINKS.ORG_ID),
                r.get(POLICY_LINKS.PROJECT_ID),
                r.get(POLICY_LINKS.USER_ID),
                r.get(POLICIES.POLICY_ID),
                r.get(POLICIES.POLICY_NAME),
                deserialize(r.value3()));
    }

    private String serialize(Map<String, Object> m) {
        if (m == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(m);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deserialize(String s) {
        if (s == null) {
            return null;
        }

        try {
            return objectMapper.readValue(s, Map.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private class PolicyRule {

        private final UUID orgId;
        private final UUID prjId;
        private final UUID userId;
        private final UUID policyId;
        private final String policyName;
        private final Map<String, Object> rules;

        private PolicyRule(UUID orgId, UUID prjId, UUID userId, UUID policyId, String policyName, Map<String, Object> rules) {
            this.orgId = orgId;
            this.prjId = prjId;
            this.userId = userId;
            this.policyId = policyId;
            this.policyName = policyName;
            this.rules = rules;
        }
    }
}
