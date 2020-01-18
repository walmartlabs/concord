package com.walmartlabs.concord.server.org.jsonstore;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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
import com.walmartlabs.concord.server.Utils;
import com.walmartlabs.concord.server.jooq.Tables;
import com.walmartlabs.concord.server.jooq.tables.Users;
import com.walmartlabs.concord.server.jooq.tables.records.JsonStoresRecord;
import com.walmartlabs.concord.server.org.EntityOwner;
import com.walmartlabs.concord.server.org.ResourceAccessEntry;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.user.UserType;
import org.jooq.*;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.JsonStoreTeamAccess.JSON_STORE_TEAM_ACCESS;
import static com.walmartlabs.concord.server.jooq.tables.JsonStores.JSON_STORES;
import static com.walmartlabs.concord.server.jooq.tables.Organizations.ORGANIZATIONS;
import static com.walmartlabs.concord.server.jooq.tables.Teams.TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.Users.USERS;
import static com.walmartlabs.concord.server.jooq.tables.VUserTeams.V_USER_TEAMS;
import static org.jooq.impl.DSL.*;

@Named
public class JsonStoreDao extends AbstractDao {

    @Inject
    public JsonStoreDao(@MainDB Configuration cfg) {
        super(cfg);
    }

    @Override
    public void tx(AbstractDao.Tx t) {
        super.tx(t);
    }

    public JsonStoreEntry get(UUID storeId) {
        try (DSLContext tx = DSL.using(cfg)) {
            return buildSelect(tx)
                    .where(JSON_STORES.JSON_STORE_ID.eq(storeId))
                    .fetchOne(JsonStoreDao::toEntity);
        }
    }

    public JsonStoreEntry get(UUID orgId, String storeName) {
        try (DSLContext tx = DSL.using(cfg)) {
            return buildSelect(tx)
                    .where(JSON_STORES.JSON_STORE_NAME.eq(storeName)
                            .and(JSON_STORES.ORG_ID.eq(orgId)))
                    .fetchOne(JsonStoreDao::toEntity);
        }
    }

    public UUID getId(UUID orgId, String storeName) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(JSON_STORES.JSON_STORE_ID)
                    .from(JSON_STORES)
                    .where(JSON_STORES.JSON_STORE_NAME.eq(storeName).and(JSON_STORES.ORG_ID.eq(orgId)))
                    .fetchOne(JSON_STORES.JSON_STORE_ID);
        }
    }

    public List<JsonStoreEntry> list(UUID orgId, UUID currentUserId, int offset, int limit, String filter) {
        try (DSLContext tx = DSL.using(cfg)) {

            SelectJoinStep<Record10<UUID, String, UUID, String, String, UUID, String, String, String, String>> q = buildSelect(tx);

            if (currentUserId != null) {
                SelectConditionStep<Record1<UUID>> teamIds = select(TEAMS.TEAM_ID)
                        .from(Tables.TEAMS)
                        .where(Tables.TEAMS.ORG_ID.eq(orgId));

                Condition filterByTeamMember = exists(selectOne().from(V_USER_TEAMS)
                        .where(V_USER_TEAMS.USER_ID.eq(currentUserId)
                                .and(V_USER_TEAMS.TEAM_ID.in(teamIds))));

                q.where(or(JSON_STORES.VISIBILITY.eq(JsonStoreVisibility.PUBLIC.toString()), filterByTeamMember));
            }

            if (filter != null) {
                q.where(JSON_STORES.JSON_STORE_NAME.containsIgnoreCase(filter));
            }

            if (offset >= 0) {
                q.offset(offset);
            }

            if (limit > 0) {
                q.limit(limit);
            }

            return q.where(JSON_STORES.ORG_ID.eq(orgId))
                    .orderBy(JSON_STORES.JSON_STORE_NAME)
                    .fetch(JsonStoreDao::toEntity);
        }
    }

    public UUID insert(UUID orgId, String name, JsonStoreVisibility visibility, UUID ownerId) {
        return txResult(tx -> insert(tx, orgId, name, visibility, ownerId));
    }

    public void update(UUID storeId, String name, JsonStoreVisibility visibility, UUID ownerId) {
        tx(tx -> update(tx, storeId, name, visibility, ownerId));
    }

    public void delete(UUID id) {
        tx(tx -> delete(tx, id));
    }


    public boolean hasAccessLevel(UUID storeId, UUID userId, ResourceAccessLevel[] levels) {
        try (DSLContext tx = DSL.using(cfg)) {
            return hasAccessLevel(tx, storeId, userId, levels);
        }
    }

    public List<ResourceAccessEntry> getAccessLevel(UUID storeId) {
        List<ResourceAccessEntry> resourceAccessList = new ArrayList<>();
        try (DSLContext tx = DSL.using(cfg)) {
            Result<Record5<UUID, UUID, String, String, String>> teamsAccess = tx.select(
                    JSON_STORE_TEAM_ACCESS.TEAM_ID,
                    JSON_STORE_TEAM_ACCESS.JSON_STORE_ID,
                    Tables.TEAMS.TEAM_NAME,
                    ORGANIZATIONS.ORG_NAME,
                    JSON_STORE_TEAM_ACCESS.ACCESS_LEVEL)
                    .from(JSON_STORE_TEAM_ACCESS)
                    .leftOuterJoin(Tables.TEAMS).on(Tables.TEAMS.TEAM_ID.eq(JSON_STORE_TEAM_ACCESS.TEAM_ID))
                    .leftOuterJoin(JSON_STORES).on(JSON_STORES.JSON_STORE_ID.eq(storeId))
                    .leftOuterJoin(ORGANIZATIONS).on(ORGANIZATIONS.ORG_ID.eq(JSON_STORES.ORG_ID))
                    .where(JSON_STORE_TEAM_ACCESS.JSON_STORE_ID.eq(storeId))
                    .fetch();

            for (Record5<UUID, UUID, String, String, String> t : teamsAccess) {
                resourceAccessList.add(new ResourceAccessEntry(t.get(JSON_STORE_TEAM_ACCESS.TEAM_ID),
                        t.get(ORGANIZATIONS.ORG_NAME),
                        t.get(Tables.TEAMS.TEAM_NAME),
                        ResourceAccessLevel.valueOf(t.get(JSON_STORE_TEAM_ACCESS.ACCESS_LEVEL))));
            }
        }
        return resourceAccessList;
    }

    public void deleteTeamAccess(DSLContext tx, UUID storeId) {
        tx.deleteFrom(JSON_STORE_TEAM_ACCESS)
                .where(JSON_STORE_TEAM_ACCESS.JSON_STORE_ID.eq(storeId))
                .execute();
    }

    public void upsertAccessLevel(UUID storeId, UUID teamId, ResourceAccessLevel level) {
        tx(tx -> upsertAccessLevel(tx, storeId, teamId, level));
    }

    public void upsertAccessLevel(DSLContext tx, UUID storeId, UUID teamId, ResourceAccessLevel level) {
        tx.insertInto(JSON_STORE_TEAM_ACCESS)
                .columns(JSON_STORE_TEAM_ACCESS.JSON_STORE_ID, JSON_STORE_TEAM_ACCESS.TEAM_ID, JSON_STORE_TEAM_ACCESS.ACCESS_LEVEL)
                .values(storeId, teamId, level.toString())
                .onDuplicateKeyUpdate()
                .set(JSON_STORE_TEAM_ACCESS.ACCESS_LEVEL, level.toString())
                .execute();
    }

    public Integer count(UUID orgId) {
        return txResult(tx -> tx.selectCount()
                .from(JSON_STORES)
                .where(JSON_STORES.ORG_ID.eq(orgId))
                .fetchOne(Record1::value1));
    }

    private UUID insert(DSLContext tx, UUID orgId, String name, JsonStoreVisibility visibility, UUID ownerId) {
        return tx.insertInto(JSON_STORES)
                .columns(JSON_STORES.OWNER_ID, JSON_STORES.JSON_STORE_NAME, JSON_STORES.ORG_ID, JSON_STORES.VISIBILITY)
                .values(ownerId, name, orgId, visibility.name())
                .returning(JSON_STORES.JSON_STORE_ID)
                .fetchOne()
                .getJsonStoreId();
    }

    @SuppressWarnings("unchecked")
    private void update(DSLContext tx, UUID storeId, String name, JsonStoreVisibility visibility, UUID ownerId) {
        UpdateSetFirstStep<JsonStoresRecord> s = tx.update(JSON_STORES);

        if (name != null) {
            s.set(JSON_STORES.JSON_STORE_NAME, name);
        }

        if (visibility != null) {
            s.set(JSON_STORES.VISIBILITY, visibility.name());
        }

        if (ownerId != null) {
            s.set(JSON_STORES.OWNER_ID, ownerId);
        }

        ((UpdateSetMoreStep<JsonStoresRecord>) s)
                .where(JSON_STORES.JSON_STORE_ID.eq(storeId))
                .execute();
    }

    private void delete(DSLContext tx, UUID id) {
        tx.deleteFrom(JSON_STORES)
                .where(JSON_STORES.JSON_STORE_ID.eq(id))
                .execute();
    }

    private boolean hasAccessLevel(DSLContext tx, UUID storeId, UUID userId, ResourceAccessLevel... levels) {
        SelectConditionStep<Record1<UUID>> teamIds = select(V_USER_TEAMS.TEAM_ID)
                .from(V_USER_TEAMS)
                .where(V_USER_TEAMS.USER_ID.eq(userId));

        return tx.fetchExists(selectFrom(JSON_STORE_TEAM_ACCESS)
                .where(JSON_STORE_TEAM_ACCESS.JSON_STORE_ID.eq(storeId)
                        .and(JSON_STORE_TEAM_ACCESS.TEAM_ID.in(teamIds))
                        .and(JSON_STORE_TEAM_ACCESS.ACCESS_LEVEL.in(Utils.toString(levels)))));
    }

    private SelectJoinStep<Record10<UUID, String, UUID, String, String, UUID, String, String, String, String>> buildSelect(DSLContext tx) {
        Users u = USERS.as("u");

        Field<String> orgNameField = select(ORGANIZATIONS.ORG_NAME)
                .from(ORGANIZATIONS)
                .where(ORGANIZATIONS.ORG_ID.eq(JSON_STORES.ORG_ID))
                .asField("orgName");

        return tx.select(JSON_STORES.JSON_STORE_ID,
                JSON_STORES.JSON_STORE_NAME,
                JSON_STORES.ORG_ID,
                orgNameField,
                JSON_STORES.VISIBILITY,
                JSON_STORES.OWNER_ID,
                u.USERNAME,
                u.DOMAIN,
                u.DISPLAY_NAME,
                u.USER_TYPE)
                .from(JSON_STORES)
                .leftJoin(u).on(u.USER_ID.eq(JSON_STORES.OWNER_ID));
    }

    private static JsonStoreEntry toEntity(Record r) {
        return JsonStoreEntry.builder()
                .id(r.getValue(JSON_STORES.JSON_STORE_ID))
                .name(r.getValue(JSON_STORES.JSON_STORE_NAME))
                .orgId(r.getValue(JSON_STORES.ORG_ID))
                .orgName(r.getValue(ORGANIZATIONS.ORG_NAME.as("orgName")))
                .visibility(JsonStoreVisibility.valueOf(r.getValue(JSON_STORES.VISIBILITY)))
                .owner(toOwner(r.get(JSON_STORES.OWNER_ID), r.get(USERS.USERNAME), r.get(USERS.DOMAIN), r.get(USERS.DISPLAY_NAME), r.get(USERS.USER_TYPE)))
                .build();
    }

    private static EntityOwner toOwner(UUID id, String username, String domain, String displayName, String userType) {
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
