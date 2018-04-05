package com.walmartlabs.concord.server.org;

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
import com.walmartlabs.concord.server.api.org.OrganizationEntry;
import com.walmartlabs.concord.server.jooq.tables.records.OrganizationsRecord;
import org.jooq.*;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.Organizations.ORGANIZATIONS;
import static com.walmartlabs.concord.server.jooq.tables.Teams.TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.UserTeams.USER_TEAMS;
import static org.jooq.impl.DSL.*;

@Named
public class OrganizationDao extends AbstractDao {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    public OrganizationDao(Configuration cfg) {
        super(cfg);
    }

    @Override
    public <T> T txResult(TxResult<T> t) {
        return super.txResult(t);
    }

    public OrganizationEntry get(UUID id) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(ORGANIZATIONS.ORG_ID, ORGANIZATIONS.ORG_NAME, ORGANIZATIONS.META.cast(String.class))
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
            return tx.select(ORGANIZATIONS.ORG_ID, ORGANIZATIONS.ORG_NAME, ORGANIZATIONS.META.cast(String.class))
                    .from(ORGANIZATIONS)
                    .where(ORGANIZATIONS.ORG_NAME.eq(name))
                    .fetchOne(this::toEntry);
        }
    }

    public UUID insert(String name, Map<String, Object> meta) {
        return txResult(tx -> insert(tx, name, meta));
    }

    public UUID insert(DSLContext tx, String name, Map<String, Object> meta) {
        return tx.insertInto(ORGANIZATIONS)
                .columns(ORGANIZATIONS.ORG_NAME, ORGANIZATIONS.META)
                .values(value(name), field("?::jsonb", serialize(meta)))
                .returning()
                .fetchOne()
                .getOrgId();
    }

    public void update(UUID id, String name, Map<String, Object> meta) {
        tx(tx -> update(tx, id, name, meta));
    }

    public void update(DSLContext tx, UUID id, String name, Map<String, Object> meta) {
        UpdateQuery<OrganizationsRecord> q = tx.updateQuery(ORGANIZATIONS);

        if (name != null) {
            q.addValue(ORGANIZATIONS.ORG_NAME, name);
        }

        if (meta != null) {
            q.addValue(ORGANIZATIONS.META, field("?::jsonb", String.class, serialize(meta)));
        }

        q.addConditions(ORGANIZATIONS.ORG_ID.eq(id));
        q.execute();
    }

    public List<OrganizationEntry> list(UUID userId) {
        try (DSLContext tx = DSL.using(cfg)) {
            SelectJoinStep<Record3<UUID, String, String>> q = tx.select(ORGANIZATIONS.ORG_ID,
                    ORGANIZATIONS.ORG_NAME,
                    ORGANIZATIONS.META.cast(String.class))
                    .from(ORGANIZATIONS);

            if (userId != null) {
                SelectConditionStep<Record1<UUID>> teamIds = selectDistinct(USER_TEAMS.TEAM_ID)
                        .from(USER_TEAMS)
                        .where(USER_TEAMS.USER_ID.eq(userId));

                SelectConditionStep<Record1<UUID>> orgIds = selectDistinct(TEAMS.ORG_ID)
                        .from(TEAMS)
                        .where(TEAMS.TEAM_ID.in(teamIds));

                q.where(ORGANIZATIONS.ORG_ID.in(orgIds));
            }

            return q.orderBy(ORGANIZATIONS.ORG_NAME)
                    .fetch(this::toEntry);
        }
    }

    private String serialize(Map<String, Object> m) {
        if (m == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(m);
        } catch (IOException e) {
            throw Throwables.propagate(e);
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
            throw Throwables.propagate(e);
        }
    }

    private OrganizationEntry toEntry(Record3<UUID, String, String> r) {
        Map<String, Object> meta = deserialize(r.value3());
        return new OrganizationEntry(r.value1(), r.value2(), meta);
    }
}
