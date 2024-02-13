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

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.Tables.V_USER_TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.Organizations.ORGANIZATIONS;
import static com.walmartlabs.concord.server.jooq.tables.Teams.TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.Users.USERS;
import static org.jooq.impl.DSL.*;

public class OrganizationDao extends AbstractDao {

    private final ConcordObjectMapper objectMapper;

    @Inject
    public OrganizationDao(@MainDB Configuration cfg,
                           ConcordObjectMapper objectMapper) {
        super(cfg);
        this.objectMapper = objectMapper;
    }

    @Override
    protected void tx(Tx t) {
        super.tx(t);
    }

    @Override
    public <T> T txResult(TxResult<T> t) {
        return super.txResult(t);
    }

    public OrganizationEntry get(UUID id) {
        return get(dsl(), id);
    }

    public OrganizationEntry get(DSLContext tx, UUID id) {
        Organizations o = ORGANIZATIONS.as("o");
        Users u = USERS.as("u");

        return tx.select(o.ORG_ID, o.ORG_NAME, o.OWNER_ID, u.USERNAME, u.DOMAIN, u.DISPLAY_NAME, u.USER_TYPE, o.VISIBILITY, o.META, o.ORG_CFG)
                .from(o)
                .leftJoin(u).on(u.USER_ID.eq(o.OWNER_ID))
                .where(o.ORG_ID.eq(id))
                .fetchOne(this::toEntry);
    }

    public UUID getId(String name) {
        return getId(dsl(), name);
    }

    public UUID getId(DSLContext tx, String name) {
        return tx.select(ORGANIZATIONS.ORG_ID)
                .from(ORGANIZATIONS)
                .where(ORGANIZATIONS.ORG_NAME.eq(name))
                .fetchOne(ORGANIZATIONS.ORG_ID);
    }

    public OrganizationEntry getByName(String name) {
        return getByName(dsl(), name);
    }

    public OrganizationEntry getByName(DSLContext tx, String name) {
        Organizations o = ORGANIZATIONS.as("o");
        Users u = USERS.as("u");

        return tx.select(o.ORG_ID, o.ORG_NAME, o.OWNER_ID, u.USERNAME, u.DOMAIN, u.DISPLAY_NAME, u.USER_TYPE, o.VISIBILITY, o.META, o.ORG_CFG)
                .from(o)
                .leftJoin(u).on(u.USER_ID.eq(o.OWNER_ID))
                .where(o.ORG_NAME.eq(name))
                .fetchOne(this::toEntry);
    }

    public Map<String, Object> getConfiguration(UUID orgId) {
        return dsl().select(ORGANIZATIONS.ORG_CFG)
                .from(ORGANIZATIONS)
                .where(ORGANIZATIONS.ORG_ID.eq(orgId))
                .fetchOne(e -> objectMapper.fromJSONB(e.value1()));
    }

    public UUID insert(String name, UUID ownerId, OrganizationVisibility visibility, Map<String, Object> meta, Map<String, Object> cfg) {
        return txResult(tx -> insert(tx, name, ownerId, visibility, meta, cfg));
    }

    public UUID insert(DSLContext tx, String name, UUID ownerId, OrganizationVisibility visibility, Map<String, Object> meta, Map<String, Object> cfg) {
        if (visibility == null) {
            visibility = OrganizationVisibility.PUBLIC;
        }

        return tx.insertInto(ORGANIZATIONS)
                .columns(ORGANIZATIONS.ORG_NAME,
                        ORGANIZATIONS.OWNER_ID,
                        ORGANIZATIONS.VISIBILITY,
                        ORGANIZATIONS.META,
                        ORGANIZATIONS.ORG_CFG)
                .values(name,
                        ownerId,
                        visibility.toString(),
                        objectMapper.toJSONB(meta),
                        objectMapper.toJSONB(cfg))
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
            q.addValue(ORGANIZATIONS.META, objectMapper.toJSONB(meta));
        }

        if (cfg != null) {
            q.addValue(ORGANIZATIONS.ORG_CFG, objectMapper.toJSONB(cfg));
        }

        q.addConditions(ORGANIZATIONS.ORG_ID.eq(id));
        q.execute();
    }

    public List<OrganizationEntry> list(UUID currentUserId, boolean onlyCurrent, int offset, int limit, String filter) {
        Organizations o = ORGANIZATIONS.as("o");
        Users u = USERS.as("u");

        SelectOnConditionStep<Record10<UUID, String, UUID, String, String, String, String, String, JSONB, JSONB>> q = dsl().select(o.ORG_ID,
                o.ORG_NAME,
                o.OWNER_ID,
                u.USERNAME,
                u.DOMAIN,
                u.DISPLAY_NAME,
                u.USER_TYPE,
                o.VISIBILITY,
                o.META,
                o.ORG_CFG)
                .from(o)
                .leftJoin(u).on(u.USER_ID.eq(o.OWNER_ID));

        if (currentUserId != null) {
            // public orgs are visible for anyone
            // but show them only if onlyCurrent == false
            // i.e. when the user specifically asked to show all available orgs
            Condition isPublic = value(onlyCurrent).isFalse()
                    .and(o.VISIBILITY.eq(OrganizationVisibility.PUBLIC.toString()));

            // check if the user belongs to a team in an org
            SelectConditionStep<Record1<UUID>> teamIds = select(TEAMS.TEAM_ID)
                    .from(TEAMS)
                    .where(TEAMS.ORG_ID.eq(o.ORG_ID));

            Condition isInATeam = exists(selectOne().from(V_USER_TEAMS)
                    .where(V_USER_TEAMS.USER_ID.eq(currentUserId)
                            .and(V_USER_TEAMS.TEAM_ID.in(teamIds))));

            // check if the user owns orgs
            Condition ownsOrgs = o.OWNER_ID.eq(currentUserId);

            // if any of those conditions true then the org must be visible
            q.where(or(isPublic, isInATeam, ownsOrgs));
        }

        if (filter != null) {
            q.where(o.ORG_NAME.containsIgnoreCase(filter));
        }

        if (offset >= 0) {
            q.offset(offset);
        }

        if (limit > 0) {
            q.limit(limit);
        }

        return q.orderBy(o.ORG_NAME)
                .fetch(this::toEntry);
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

    private OrganizationEntry toEntry(Record10<UUID, String, UUID, String, String, String, String, String, JSONB, JSONB> r) {
        Map<String, Object> meta = objectMapper.fromJSONB(r.value9());
        Map<String, Object> cfg = objectMapper.fromJSONB(r.value10());
        return new OrganizationEntry(r.value1(), r.value2(),
                toOwner(r.value3(), r.value4(), r.value5(), r.value6(), r.value7()),
                OrganizationVisibility.valueOf(r.value8()), meta, cfg);
    }

    private EntityOwner toOwner(UUID id, String username, String domain, String displayName, String userType) {
        if (id == null) {
            return null;
        }
        return EntityOwner.builder()
                .id(id)
                .username(username)
                .userDomain(domain)
                .displayName(displayName)
                .userType(UserType.valueOf(userType))
                .build();
    }
}
