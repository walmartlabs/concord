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
import com.walmartlabs.concord.db.PgUtils;
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.Utils;
import com.walmartlabs.concord.server.jooq.tables.Organizations;
import com.walmartlabs.concord.server.jooq.tables.Projects;
import com.walmartlabs.concord.server.jooq.tables.Repositories;
import org.jooq.*;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.db.PgUtils.jsonbText;
import static com.walmartlabs.concord.server.jooq.Tables.*;
import static com.walmartlabs.concord.server.jooq.tables.Triggers.TRIGGERS;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.value;

public class TriggersDao extends AbstractDao {

    private final ConcordObjectMapper objectMapper;

    @Inject
    public TriggersDao(@MainDB Configuration cfg,
                       ConcordObjectMapper objectMapper) {
        super(cfg);

        this.objectMapper = objectMapper;
    }

    public TriggerEntry get(UUID id) {
        SelectJoinStep<Record12<UUID, UUID, String, UUID, String, UUID, String, String, String[], JSONB, JSONB, JSONB>> query = selectTriggers(dsl());

        return query.where(TRIGGERS.TRIGGER_ID.eq(id))
                .fetchOne(this::toEntity);
    }

    public UUID insert(DSLContext tx, UUID projectId, UUID repositoryId, String eventSource, List<String> activeProfiles, Map<String, Object> args, Map<String, Object> conditions, Map<String, Object> config) {
        return tx.insertInto(TRIGGERS)
                .columns(TRIGGERS.PROJECT_ID, TRIGGERS.REPO_ID, TRIGGERS.EVENT_SOURCE, TRIGGERS.ACTIVE_PROFILES, TRIGGERS.ARGUMENTS, TRIGGERS.CONDITIONS, TRIGGERS.TRIGGER_CFG)
                .values(projectId, repositoryId, eventSource, Utils.toArray(activeProfiles), objectMapper.toJSONB(args), objectMapper.toJSONB(conditions), objectMapper.toJSONB(config))
                .returning(TRIGGERS.TRIGGER_ID)
                .fetchOne()
                .getTriggerId();
    }

    public void delete(UUID projectId, UUID repositoryId) {
        tx(tx -> delete(tx, projectId, repositoryId));
    }

    public void delete(DSLContext tx, List<UUID> triggerIds) {
        tx.delete(TRIGGERS)
                .where(TRIGGERS.TRIGGER_ID.in(triggerIds))
                .execute();
    }

    public void delete(DSLContext tx, UUID projectId, UUID repositoryId) {
        tx.delete(TRIGGERS)
                .where(TRIGGERS.PROJECT_ID.eq(projectId).and(TRIGGERS.REPO_ID.eq(repositoryId)))
                .execute();
    }

    public List<TriggerEntry> list(UUID projectId, UUID repositoryId) {
        return list(dsl(), projectId, repositoryId);
    }

    public List<TriggerEntry> list(DSLContext tx, UUID projectId, UUID repositoryId) {
        SelectJoinStep<Record12<UUID, UUID, String, UUID, String, UUID, String, String, String[], JSONB, JSONB, JSONB>> query = selectTriggers(tx);

        return query.where(TRIGGERS.PROJECT_ID.eq(projectId).and(TRIGGERS.REPO_ID.eq(repositoryId)))
                .fetch(this::toEntity);
    }

    public List<TriggerEntry> list(String eventSource, Integer version) {
        return list(dsl(), null, eventSource, version, null);
    }

    public List<TriggerEntry> list(String eventSource, Integer version, Map<String, String> conditions) {
        return list(dsl(), null, eventSource, version, conditions);
    }

    public List<TriggerEntry> list(UUID projectId, String eventSource, Integer version, Map<String, String> conditions) {
        return list(dsl(), projectId, eventSource, version, conditions);
    }

    private List<TriggerEntry> list(DSLContext tx, UUID projectId, String eventSource, Integer version, Map<String, String> conditions) {
        SelectJoinStep<Record12<UUID, UUID, String, UUID, String, UUID, String, String, String[], JSONB, JSONB, JSONB>> query = selectTriggers(tx);

        Condition w = TRIGGERS.EVENT_SOURCE.eq(eventSource);
        if (projectId != null) {
            w = w.and(TRIGGERS.PROJECT_ID.eq(projectId));
        }

        if (version != null) {
            Condition v = jsonbText(TRIGGERS.CONDITIONS, "version").eq(String.valueOf(version));
            if (version == 1) {
                v = v.or(PgUtils.jsonbText(TRIGGERS.CONDITIONS, "version").isNull());
            }
            w = w.and(v);
        }

        return query.where(appendConditionClause(conditions, w))
                .fetch(this::toEntity);
    }

    public List<TriggerEntry> list(UUID orgId, UUID projectId, UUID repositoryId, String type) {
        SelectJoinStep<Record12<UUID, UUID, String, UUID, String, UUID, String, String, String[], JSONB, JSONB, JSONB>> query = selectTriggers(dsl());

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

    private SelectJoinStep<Record12<UUID, UUID, String, UUID, String, UUID, String, String, String[], JSONB, JSONB, JSONB>> selectTriggers(DSLContext tx) {
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
                TRIGGERS.ARGUMENTS,
                TRIGGERS.CONDITIONS,
                TRIGGERS.TRIGGER_CFG)
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
                    jsonbText(TRIGGERS.CONDITIONS, e.getKey()).isNull()
                            .or(
                                    value(e.getValue()).likeRegex(jsonbText(TRIGGERS.CONDITIONS, e.getKey()).cast(String.class))));
        }
        return w;
    }

    private TriggerEntry toEntity(Record12<UUID, UUID, String, UUID, String, UUID, String, String, String[], JSONB, JSONB, JSONB> item) {
        List<String> activeProfiles = null;
        if (item.value9() != null) {
            activeProfiles = Arrays.asList(item.value9());
        }

        return new TriggerEntry(item.value1(), item.value2(), item.value3(), item.value4(), item.value5(), item.value6(),
                item.value7(), item.value8(), activeProfiles,
                objectMapper.fromJSONB(item.value10()),
                objectMapper.fromJSONB(item.value11()),
                objectMapper.fromJSONB(item.value12()));
    }
}
