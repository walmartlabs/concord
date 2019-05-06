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

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.jooq.tables.Organizations;
import com.walmartlabs.concord.server.jooq.tables.Users;
import com.walmartlabs.concord.server.jooq.tables.records.OrganizationsRecord;
import com.walmartlabs.concord.server.org.team.TeamRole;
import com.walmartlabs.concord.server.user.UserType;
import org.jooq.*;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.Tables.V_USER_TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.Organizations.ORGANIZATIONS;
import static com.walmartlabs.concord.server.jooq.tables.Teams.TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.Users.USERS;
import static org.jooq.impl.DSL.*;

@Named
public class OrganizationDao extends AbstractDao {

    private final ConcordObjectMapper objectMapper;

    @Inject
    public OrganizationDao(@MainDB Configuration cfg,
                           ConcordObjectMapper objectMapper) {
        super(cfg);
        this.objectMapper = objectMapper;
    }

    @Override
    public <T> T txResult(TxResult<T> t) {
        return super.txResult(t);
    }

    public OrganizationEntry get(UUID id) {
        try (DSLContext tx = DSL.using(cfg)) {
            Organizations o = ORGANIZATIONS.as("o");
            Users u = USERS.as("u");

            return tx.select(o.ORG_ID, o.ORG_NAME, o.OWNER_ID, u.USERNAME, u.DISPLAY_NAME, u.USER_TYPE, o.VISIBILITY,
                    o.META.cast(String.class), o.ORG_CFG.cast(String.class))
                    .from(o)
                    .leftJoin(u).on(u.USER_ID.eq(o.OWNER_ID))
                    .where(o.ORG_ID.eq(id))
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
            Organizations o = ORGANIZATIONS.as("o");
            Users u = USERS.as("u");

            return tx.select(o.ORG_ID, o.ORG_NAME, o.OWNER_ID, u.USERNAME, u.DISPLAY_NAME, u.USER_TYPE, o.VISIBILITY,
                    o.META.cast(String.class), o.ORG_CFG.cast(String.class))
                    .from(o)
                    .leftJoin(u).on(u.USER_ID.eq(o.OWNER_ID))
                    .where(o.ORG_NAME.eq(name))
                    .fetchOne(this::toEntry);
        }
    }

    public Map<String, Object> getConfiguration(UUID orgId) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(ORGANIZATIONS.ORG_CFG.cast(String.class))
                    .from(ORGANIZATIONS)
                    .where(ORGANIZATIONS.ORG_ID.eq(orgId))
                    .fetchOne(e -> objectMapper.deserialize(e.value1()));
        }
    }

    public UUID insert(String name, UUID ownerId, OrganizationVisibility visibility, Map<String, Object> meta, Map<String, Object> cfg) {
        return txResult(tx -> insert(tx, name, ownerId, visibility, meta, cfg));
    }

    public UUID insert(DSLContext tx, String name, UUID ownerId, OrganizationVisibility visibility, Map<String, Object> meta, Map<String, Object> cfg) {
        if (visibility == null) {
            visibility = OrganizationVisibility.PUBLIC;
        }

        return tx.insertInto(ORGANIZATIONS)
                .columns(ORGANIZATIONS.ORG_NAME, ORGANIZATIONS.OWNER_ID, ORGANIZATIONS.VISIBILITY, ORGANIZATIONS.META, ORGANIZATIONS.ORG_CFG)
                .values(value(name), value(ownerId), value(visibility.toString()),
                        field("?::jsonb", objectMapper.serialize(meta)), field("?::jsonb", objectMapper.serialize(cfg)))
                .returning()
                .fetchOne()
                .getOrgId();
    }

    public void update(UUID id, String name, UUID ownerId, OrganizationVisibility visibility, Map<String, Object> meta, Map<String, Object> cfg) {
        tx(tx -> update(tx, id, name, ownerId, visibility, meta, cfg));
    }

    public void update(DSLContext tx, UUID id, String name, UUID ownerId, OrganizationVisibility visibility, Map<String, Object> meta, Map<String, Object> cfg) {
        UpdateQuery<OrganizationsRecord> q = tx.updateQuery(ORGANIZATIONS);

        if (name != null) {
            q.addValue(ORGANIZATIONS.ORG_NAME, name);
        }

        if (ownerId != null) {
            q.addValue(ORGANIZATIONS.OWNER_ID, ownerId);
        }

        if (visibility != null) {
            q.addValue(ORGANIZATIONS.VISIBILITY, visibility.toString());
        }

        if (meta != null) {
            q.addValue(ORGANIZATIONS.META, field("?::jsonb", String.class, objectMapper.serialize(meta)));
        }

        if (cfg != null) {
            q.addValue(ORGANIZATIONS.ORG_CFG, field("?::jsonb", String.class, objectMapper.serialize(cfg)));
        }

        q.addConditions(ORGANIZATIONS.ORG_ID.eq(id));
        q.execute();
    }

    public List<OrganizationEntry> list(UUID userId) {
        try (DSLContext tx = DSL.using(cfg)) {
            Organizations o = ORGANIZATIONS.as("o");
            Users u = USERS.as("u");

            SelectOnConditionStep<Record9<UUID, String, UUID, String, String, String, String, String, String>> q = tx.select(o.ORG_ID,
                    o.ORG_NAME,
                    o.OWNER_ID,
                    u.USERNAME,
                    u.DISPLAY_NAME,
                    u.USER_TYPE,
                    o.VISIBILITY,
                    o.META.cast(String.class),
                    o.ORG_CFG.cast(String.class))
                    .from(o)
                    .leftJoin(u).on(u.USER_ID.eq(o.OWNER_ID));

            if (userId != null) {
                SelectConditionStep<Record1<UUID>> teamIds = selectDistinct(V_USER_TEAMS.TEAM_ID)
                        .from(V_USER_TEAMS)
                        .where(V_USER_TEAMS.USER_ID.eq(userId));

                SelectConditionStep<Record1<UUID>> orgIds = selectDistinct(TEAMS.ORG_ID)
                        .from(TEAMS)
                        .where(TEAMS.TEAM_ID.in(teamIds));

                q.where(o.ORG_ID.in(orgIds));
            }

            return q.orderBy(o.ORG_NAME)
                    .fetch(this::toEntry);
        }
    }

    public boolean hasOwner(DSLContext tx, UUID orgId) {
        return tx.select(ORGANIZATIONS.OWNER_ID).from(ORGANIZATIONS)
                .where(ORGANIZATIONS.ORG_ID.eq(orgId))
                .fetchOne() != null;
    }

    public boolean hasRole(DSLContext tx, UUID orgId, TeamRole role) {
        SelectConditionStep<Record1<UUID>> teamIds = select(TEAMS.TEAM_ID).from(TEAMS).where(TEAMS.ORG_ID.eq(orgId));

        return tx.fetchExists(select(V_USER_TEAMS.USER_ID)
                .from(V_USER_TEAMS)
                .where(V_USER_TEAMS.TEAM_ROLE.eq(role.toString())
                        .and(V_USER_TEAMS.TEAM_ID.in(teamIds))));
    }

    public void delete(UUID orgId) {
        tx(tx -> tx.deleteFrom(ORGANIZATIONS)
                .where(ORGANIZATIONS.ORG_ID.eq(orgId))
                .execute());
    }

    private OrganizationEntry toEntry(Record9<UUID, String, UUID, String, String, String, String, String, String> r) {
        Map<String, Object> meta = objectMapper.deserialize(r.value8());
        Map<String, Object> cfg = objectMapper.deserialize(r.value9());
        return new OrganizationEntry(r.value1(), r.value2(),
                toOwner(r.value3(), r.value4(), r.value5(), r.value6()),
                OrganizationVisibility.valueOf(r.value7()), meta, cfg);
    }

    private EntityOwner toOwner(UUID id, String username, String displayName, String type) {
        if (id == null) {
            return null;
        }

        return EntityOwner.of(id, username, displayName, UserType.valueOf(type));
    }
}
