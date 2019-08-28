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
import org.jooq.*;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.db.PgUtils.jsonText;
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

    public void update(DSLContext tx, UUID triggerId, Map<String, Object> conditions, int version) {
        tx.update(TRIGGERS)
                .set(TRIGGERS.TRIGGER_VERSION, value(version))
                .set(TRIGGERS.CONDITIONS, field("?::jsonb", String.class, objectMapper.serialize(conditions)))
                .where(TRIGGERS.TRIGGER_ID.eq(triggerId))
                .execute();
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
            return list(tx, null, eventSource, null, null);
        }
    }

    public List<TriggerEntry> list(UUID projectId, String eventSource, Integer version, Map<String, String> conditions) {
        try (DSLContext tx = DSL.using(cfg)) {
            return list(tx, projectId, eventSource, version, conditions);
        }
    }

    private List<TriggerEntry> list(DSLContext tx, UUID projectId, String eventSource, Integer version, Map<String, String> conditions) {
        SelectJoinStep<Record12<UUID, UUID, String, UUID, String, UUID, String, String, String[], String, String, String>> query = selectTriggers(tx);

        Condition w = TRIGGERS.EVENT_SOURCE.eq(eventSource);
        if (projectId != null) {
            w = w.and(TRIGGERS.PROJECT_ID.eq(projectId));
        }

        if (version != null) {
            w = w.and(TRIGGERS.TRIGGER_VERSION.eq(version));
        }

        return query.where(appendConditionClause(conditions, w))
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

    private Condition appendConditionClause(Map<String, String> conditions, Condition w) {
        if (conditions == null) {
            return w;
        }

        for (Map.Entry<String, String> e : conditions.entrySet()) {
            w = w.and(
                    jsonText(TRIGGERS.CONDITIONS, e.getKey()).isNull()
                            .or(
                                    value(e.getValue()).likeRegex(jsonText(TRIGGERS.CONDITIONS, e.getKey()).cast(String.class))));
        }
        return w;
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
