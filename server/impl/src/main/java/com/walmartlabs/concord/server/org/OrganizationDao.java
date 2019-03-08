package com.walmartlabs.concord.server.org;

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
import com.walmartlabs.concord.server.jooq.tables.records.OrganizationsRecord;
import com.walmartlabs.concord.server.org.team.TeamRole;
import org.jooq.*;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.Tables.V_USER_TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.Organizations.ORGANIZATIONS;
import static com.walmartlabs.concord.server.jooq.tables.Teams.TEAMS;
import static org.jooq.impl.DSL.*;

@Named
public class OrganizationDao extends AbstractDao {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    public OrganizationDao(@Named("app") Configuration cfg) {
        super(cfg);
    }

    @Override
    public <T> T txResult(TxResult<T> t) {
        return super.txResult(t);
    }

    public OrganizationEntry get(UUID id) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(ORGANIZATIONS.ORG_ID, ORGANIZATIONS.ORG_NAME, ORGANIZATIONS.VISIBILITY,
                    ORGANIZATIONS.META.cast(String.class), ORGANIZATIONS.ORG_CFG.cast(String.class))
                    .from(ORGANIZATIONS)
                    .where(ORGANIZATIONS.ORG_ID.eq(id))
                    .fetchOne(this::toEntry);
        }
    }

    public UUID getId(String name) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(ORGANIZATIONS.ORG_ID)
                    .from(ORGANIZATIONS)
                    .where(ORGANIZATIONS.ORG_NAME.eq(name))
                    .fetchOne(ORGANIZATIONS.ORG_ID);
        }
    }

    public OrganizationEntry getByName(String name) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(ORGANIZATIONS.ORG_ID, ORGANIZATIONS.ORG_NAME, ORGANIZATIONS.VISIBILITY,
                    ORGANIZATIONS.META.cast(String.class), ORGANIZATIONS.ORG_CFG.cast(String.class))
                    .from(ORGANIZATIONS)
                    .where(ORGANIZATIONS.ORG_NAME.eq(name))
                    .fetchOne(this::toEntry);
        }
    }

    public Map<String, Object> getConfiguration(UUID orgId) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(ORGANIZATIONS.ORG_CFG.cast(String.class))
                    .from(ORGANIZATIONS)
                    .where(ORGANIZATIONS.ORG_ID.eq(orgId))
                    .fetchOne(e -> deserialize(e.value1()));
        }
    }

    public UUID insert(String name, OrganizationVisibility visibility, Map<String, Object> meta, Map<String, Object> cfg) {
        return txResult(tx -> insert(tx, name, visibility, meta, cfg));
    }

    public UUID insert(DSLContext tx, String name, OrganizationVisibility visibility, Map<String, Object> meta, Map<String, Object> cfg) {
        if (visibility == null) {
            visibility = OrganizationVisibility.PUBLIC;
        }

        return tx.insertInto(ORGANIZATIONS)
                .columns(ORGANIZATIONS.ORG_NAME, ORGANIZATIONS.VISIBILITY, ORGANIZATIONS.META, ORGANIZATIONS.ORG_CFG)
                .values(value(name), value(visibility.toString()),
                        field("?::jsonb", serialize(meta)), field("?::jsonb", serialize(cfg)))
                .returning()
                .fetchOne()
                .getOrgId();
    }

    public void update(UUID id, String name, OrganizationVisibility visibility, Map<String, Object> meta, Map<String, Object> cfg) {
        tx(tx -> update(tx, id, name, visibility, meta, cfg));
    }

    public void update(DSLContext tx, UUID id, String name, OrganizationVisibility visibility, Map<String, Object> meta, Map<String, Object> cfg) {
        UpdateQuery<OrganizationsRecord> q = tx.updateQuery(ORGANIZATIONS);

        if (name != null) {
            q.addValue(ORGANIZATIONS.ORG_NAME, name);
        }

        if (visibility != null) {
            q.addValue(ORGANIZATIONS.VISIBILITY, visibility.toString());
        }

        if (meta != null) {
            q.addValue(ORGANIZATIONS.META, field("?::jsonb", String.class, serialize(meta)));
        }

        if (cfg != null) {
            q.addValue(ORGANIZATIONS.ORG_CFG, field("?::jsonb", String.class, serialize(cfg)));
        }

        q.addConditions(ORGANIZATIONS.ORG_ID.eq(id));
        q.execute();
    }

    public List<OrganizationEntry> list(UUID userId) {
        try (DSLContext tx = DSL.using(cfg)) {
            SelectJoinStep<Record5<UUID, String, String, String, String>> q = tx.select(ORGANIZATIONS.ORG_ID,
                    ORGANIZATIONS.ORG_NAME,
                    ORGANIZATIONS.VISIBILITY,
                    ORGANIZATIONS.META.cast(String.class),
                    ORGANIZATIONS.ORG_CFG.cast(String.class))
                    .from(ORGANIZATIONS);

            if (userId != null) {
                SelectConditionStep<Record1<UUID>> teamIds = selectDistinct(V_USER_TEAMS.TEAM_ID)
                        .from(V_USER_TEAMS)
                        .where(V_USER_TEAMS.USER_ID.eq(userId));

                SelectConditionStep<Record1<UUID>> orgIds = selectDistinct(TEAMS.ORG_ID)
                        .from(TEAMS)
                        .where(TEAMS.TEAM_ID.in(teamIds));

                q.where(ORGANIZATIONS.ORG_ID.in(orgIds));
            }

            return q.orderBy(ORGANIZATIONS.ORG_NAME)
                    .fetch(this::toEntry);
        }
    }

    public boolean hasRole(DSLContext tx, UUID orgId, TeamRole role) {
        SelectConditionStep<Record1<UUID>> teamIds = select(TEAMS.TEAM_ID).from(TEAMS).where(TEAMS.ORG_ID.eq(orgId));

        return tx.fetchExists(select(V_USER_TEAMS.USER_ID)
                .from(V_USER_TEAMS)
                .where(V_USER_TEAMS.TEAM_ROLE.eq(role.toString())
                        .and(V_USER_TEAMS.TEAM_ID.in(teamIds))));
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

    private OrganizationEntry toEntry(Record5<UUID, String, String, String, String> r) {
        Map<String, Object> meta = deserialize(r.value4());
        Map<String, Object> cfg = deserialize(r.value5());
        return new OrganizationEntry(r.value1(), r.value2(), OrganizationVisibility.valueOf(r.value3()), meta, cfg);
    }
}
