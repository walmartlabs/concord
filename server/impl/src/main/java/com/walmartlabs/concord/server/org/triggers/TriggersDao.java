package com.walmartlabs.concord.server.org.triggers;

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
import com.walmartlabs.concord.server.Utils;
import com.walmartlabs.concord.server.jooq.tables.Organizations;
import com.walmartlabs.concord.server.jooq.tables.Projects;
import com.walmartlabs.concord.server.jooq.tables.Repositories;
import com.walmartlabs.concord.server.jooq.tables.Triggers;
import org.jooq.*;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.Tables.*;
import static com.walmartlabs.concord.server.jooq.tables.Triggers.TRIGGERS;
import static org.jooq.impl.DSL.*;

@Named
public class TriggersDao extends AbstractDao {

    private final ConcordObjectMapper objectMapper;

    @Inject
    public TriggersDao(@MainDB Configuration cfg,
                       ConcordObjectMapper objectMapper) {
        super(cfg);

        this.objectMapper = objectMapper;
    }

    public TriggerEntry get(UUID id) {
        try (DSLContext tx = DSL.using(cfg)) {
            SelectJoinStep<Record12<UUID, UUID, String, UUID, String, UUID, String, String, String[], String, String, String>> query = selectTriggers(tx);

            return query.where(TRIGGERS.TRIGGER_ID.eq(id))
                    .fetchOne(this::toEntity);
        }
    }

    public UUID insert(UUID projectId, UUID repositoryId, String eventSource, List<String> activeProfiles, Map<String, Object> args, Map<String, Object> conditions, Map<String, Object> config) {
        return txResult(tx -> insert(tx, projectId, repositoryId, eventSource, activeProfiles, args, conditions, config));
    }

    public UUID insert(DSLContext tx, UUID projectId, UUID repositoryId, String eventSource, List<String> activeProfiles, Map<String, Object> args, Map<String, Object> conditions, Map<String, Object> config) {
        return tx.insertInto(TRIGGERS)
                .columns(TRIGGERS.PROJECT_ID, TRIGGERS.REPO_ID, TRIGGERS.EVENT_SOURCE, TRIGGERS.ACTIVE_PROFILES, TRIGGERS.ARGUMENTS, TRIGGERS.CONDITIONS, TRIGGERS.TRIGGER_CFG)
                .values(projectId, repositoryId, eventSource, Utils.toArray(activeProfiles), field("?::jsonb", objectMapper.serialize(args)), field("?::jsonb", objectMapper.serialize(conditions)), field("?::jsonb", objectMapper.serialize(config)))
                .returning(TRIGGERS.TRIGGER_ID)
                .fetchOne()
                .getTriggerId();
    }

    public void update(UUID id, UUID projectId, UUID repositoryId, String eventSource, List<String> activeProfiles, Map<String, Object> args, Map<String, Object> conditions, Map<String, Object> config) {
        tx(tx -> update(tx, id, projectId, repositoryId, eventSource, activeProfiles, args, conditions, config));
    }

    private void update(DSLContext tx, UUID id, UUID projectId, UUID repositoryId, String eventSource, List<String> activeProfiles, Map<String, Object> args, Map<String, Object> conditions, Map<String, Object> config) {
        tx.update(TRIGGERS)
                .set(TRIGGERS.PROJECT_ID, projectId)
                .set(TRIGGERS.REPO_ID, repositoryId)
                .set(TRIGGERS.EVENT_SOURCE, eventSource)
                .set(TRIGGERS.ACTIVE_PROFILES, Utils.toArray(activeProfiles))
                .set(TRIGGERS.ARGUMENTS, field("?::jsonb", String.class, objectMapper.serialize(args)))
                .set(TRIGGERS.CONDITIONS, field("?::jsonb", String.class, objectMapper.serialize(conditions)))
                .set(TRIGGERS.TRIGGER_CFG, field("?::jsonb", String.class, objectMapper.serialize(config)))
                .where(TRIGGERS.TRIGGER_ID.eq(id))
                .execute();
    }

    public void delete(UUID id) {
        tx(tx -> tx.delete(TRIGGERS)
                .where(TRIGGERS.TRIGGER_ID.eq(id))
                .execute());
    }

    public void delete(DSLContext tx, UUID projectId, UUID repositoryId) {
        tx.delete(TRIGGERS)
                .where(TRIGGERS.PROJECT_ID.eq(projectId).and(TRIGGERS.REPO_ID.eq(repositoryId)))
                .execute();
    }

    public List<TriggerEntry> list(UUID projectId, UUID repositoryId) {
        try (DSLContext tx = DSL.using(cfg)) {
            SelectJoinStep<Record12<UUID, UUID, String, UUID, String, UUID, String, String, String[], String, String, String>> query = selectTriggers(tx);

            return query.where(TRIGGERS.PROJECT_ID.eq(projectId).and(TRIGGERS.REPO_ID.eq(repositoryId)))
                    .fetch(this::toEntity);
        }
    }

    public List<TriggerEntry> list(String eventSource) {
        try (DSLContext tx = DSL.using(cfg)) {
            return list(tx, eventSource, null);
        }
    }

    public List<TriggerEntry> list(String eventSource, Map<String, String> conditions) {
        try (DSLContext tx = DSL.using(cfg)) {
            return list(tx, eventSource, conditions);
        }
    }

    public List<TriggerEntry> list(DSLContext tx, String eventSource, Map<String, String> conditions) {
        SelectJoinStep<Record12<UUID, UUID, String, UUID, String, UUID, String, String, String[], String, String, String>> query = selectTriggers(tx);

        return query.where(buildConditionClause(TRIGGERS, eventSource, conditions))
                .fetch(this::toEntity);
    }

    public List<TriggerEntry> list(UUID orgId, UUID projectId, UUID repositoryId, String type) {
        try (DSLContext tx = DSL.using(cfg)) {
            SelectJoinStep<Record12<UUID, UUID, String, UUID, String, UUID, String, String, String[], String, String, String>> query = selectTriggers(tx);

            if (orgId != null) {
                SelectConditionStep<Record1<UUID>> projectIds = select(PROJECTS.PROJECT_ID)
                        .from(PROJECTS)
                        .where(PROJECTS.ORG_ID.eq(orgId));

                query.where(TRIGGERS.PROJECT_ID.in(projectIds));
            }

            if (projectId != null) {
                query.where(TRIGGERS.PROJECT_ID.eq(projectId));
            }

            if (repositoryId != null) {
                query.where(TRIGGERS.REPO_ID.eq(repositoryId));
            }

            if (type != null) {
                query.where(TRIGGERS.EVENT_SOURCE.eq(type));
            }

            return query.fetch(this::toEntity);
        }
    }

    private SelectJoinStep<Record12<UUID, UUID, String, UUID, String, UUID, String, String, String[], String, String, String>> selectTriggers(DSLContext tx) {
        Organizations o = ORGANIZATIONS.as("o");
        Projects p = PROJECTS.as("p");
        Repositories r = REPOSITORIES.as("r");

        return tx.select(
                TRIGGERS.TRIGGER_ID,
                o.ORG_ID,
                o.ORG_NAME,
                TRIGGERS.PROJECT_ID,
                p.PROJECT_NAME,
                TRIGGERS.REPO_ID,
                r.REPO_NAME,
                TRIGGERS.EVENT_SOURCE,
                TRIGGERS.ACTIVE_PROFILES,
                TRIGGERS.ARGUMENTS.cast(String.class),
                TRIGGERS.CONDITIONS.cast(String.class),
                TRIGGERS.TRIGGER_CFG.cast(String.class))
                .from(TRIGGERS)
                .leftJoin(p).on(p.PROJECT_ID.eq(TRIGGERS.PROJECT_ID))
                .leftJoin(o).on(o.ORG_ID.eq(p.ORG_ID))
                .leftJoin(r).on(r.REPO_ID.eq(TRIGGERS.REPO_ID));
    }

    private Condition buildConditionClause(Triggers t, String eventSource, Map<String, String> conditions) {
        Condition result = t.EVENT_SOURCE.eq(eventSource);
        if (conditions == null) {
            return result;
        }

        for (Map.Entry<String, String> e : conditions.entrySet()) {
            result = result.and(
                    jsonText(t.CONDITIONS, e.getKey()).isNull()
                            .or(
                                    value(e.getValue()).likeRegex(jsonText(t.CONDITIONS, e.getKey()).cast(String.class))));
        }
        return result;
    }

    private static Field<Object> jsonText(Field<?> field, String name) {
        return field("{0}->>{1}", Object.class, field, inline(name));
    }

    private TriggerEntry toEntity(Record12<UUID, UUID, String, UUID, String, UUID, String, String, String[], String, String, String> item) {
        List<String> activeProfiles = null;
        if (item.value9() != null) {
            activeProfiles = Arrays.asList(item.value9());
        }

        return new TriggerEntry(item.value1(), item.value2(), item.value3(), item.value4(), item.value5(), item.value6(),
                item.value7(), item.value8(), activeProfiles,
                objectMapper.deserialize(item.value10()),
                objectMapper.deserialize(item.value11()),
                objectMapper.deserialize(item.value12()));
    }
}
