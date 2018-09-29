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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.Utils;
import com.walmartlabs.concord.server.jooq.tables.Triggers;
import org.jooq.*;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.Tables.PROJECTS;
import static com.walmartlabs.concord.server.jooq.Tables.REPOSITORIES;
import static com.walmartlabs.concord.server.jooq.tables.Triggers.TRIGGERS;
import static org.jooq.impl.DSL.*;

@Named
public class TriggersDao extends AbstractDao {

    private final ObjectMapper objectMapper;

    @Inject
    public TriggersDao(@Named("app") Configuration cfg) {
        super(cfg);

        this.objectMapper = new ObjectMapper();
    }

    public TriggerEntry get(UUID id) {
        Triggers t = TRIGGERS.as("t");

        Field<String> repositoryNameField = select(REPOSITORIES.REPO_NAME).from(REPOSITORIES).where(REPOSITORIES.REPO_ID.eq(t.REPO_ID)).asField();
        Field<String> projectNameField = select(PROJECTS.PROJECT_NAME).from(PROJECTS).where(PROJECTS.PROJECT_ID.eq(t.PROJECT_ID)).asField();

        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(t.TRIGGER_ID,
                    t.PROJECT_ID,
                    projectNameField,
                    t.REPO_ID,
                    repositoryNameField,
                    t.EVENT_SOURCE,
                    t.ACTIVE_PROFILES,
                    t.ARGUMENTS.cast(String.class),
                    t.CONDITIONS.cast(String.class),
                    t.TRIGGER_CFG.cast(String.class))
                    .from(t)
                    .where(t.TRIGGER_ID.eq(id))
                    .fetchOne(this::toEntity);
        }
    }

    public UUID insert(UUID projectId, UUID repositoryId, String eventSource, List<String> activeProfiles, Map<String, Object> args, Map<String, Object> conditions, Map<String, Object> config) {
        return txResult(tx -> insert(tx, projectId, repositoryId, eventSource, activeProfiles, args, conditions, config));
    }

    public UUID insert(DSLContext tx, UUID projectId, UUID repositoryId, String eventSource, List<String> activeProfiles, Map<String, Object> args, Map<String, Object> conditions, Map<String, Object> config) {
        return tx.insertInto(TRIGGERS)
                .columns(TRIGGERS.PROJECT_ID, TRIGGERS.REPO_ID, TRIGGERS.EVENT_SOURCE, TRIGGERS.ACTIVE_PROFILES, TRIGGERS.ARGUMENTS, TRIGGERS.CONDITIONS, TRIGGERS.TRIGGER_CFG)
                .values(projectId, repositoryId, eventSource, Utils.toArray(activeProfiles), field("?::jsonb", serialize(args)), field("?::jsonb", serialize(conditions)), field("?::jsonb", serialize(config)))
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
                .set(TRIGGERS.ARGUMENTS, field("?::jsonb", String.class, serialize(args)))
                .set(TRIGGERS.CONDITIONS, field("?::jsonb", String.class, serialize(conditions)))
                .set(TRIGGERS.TRIGGER_CFG, field("?::jsonb", String.class, serialize(config)))
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
            Triggers t = TRIGGERS.as("t");

            Field<String> repositoryNameField = select(REPOSITORIES.REPO_NAME).from(REPOSITORIES).where(REPOSITORIES.REPO_ID.eq(t.REPO_ID)).asField();
            Field<String> projectNameField = select(PROJECTS.PROJECT_NAME).from(PROJECTS).where(PROJECTS.PROJECT_ID.eq(t.PROJECT_ID)).asField();

            return tx.select(t.TRIGGER_ID,
                    t.PROJECT_ID,
                    projectNameField,
                    t.REPO_ID,
                    repositoryNameField,
                    t.EVENT_SOURCE,
                    t.ACTIVE_PROFILES,
                    t.ARGUMENTS.cast(String.class),
                    t.CONDITIONS.cast(String.class),
                    t.TRIGGER_CFG.cast(String.class))
                    .from(t)
                    .where(t.PROJECT_ID.eq(projectId).and(t.REPO_ID.eq(repositoryId)))
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
        Triggers t = TRIGGERS.as("t");

        Field<String> repositoryNameField = select(REPOSITORIES.REPO_NAME).from(REPOSITORIES).where(REPOSITORIES.REPO_ID.eq(t.REPO_ID)).asField();
        Field<String> projectNameField = select(PROJECTS.PROJECT_NAME).from(PROJECTS).where(PROJECTS.PROJECT_ID.eq(t.PROJECT_ID)).asField();

        return tx.select(t.TRIGGER_ID,
                t.PROJECT_ID,
                projectNameField,
                t.REPO_ID,
                repositoryNameField,
                t.EVENT_SOURCE,
                t.ACTIVE_PROFILES,
                t.ARGUMENTS.cast(String.class),
                t.CONDITIONS.cast(String.class),
                t.TRIGGER_CFG.cast(String.class))
                .from(t)
                .where(buildConditionClause(t, eventSource, conditions))
                .fetch(this::toEntity);
    }

    private Condition buildConditionClause(Triggers t, String eventSource, Map<String, String> conditions) {
        Condition result = t.EVENT_SOURCE.eq(eventSource);
        if (conditions == null) {
            return result;
        }

        for(Map.Entry<String, String> e : conditions.entrySet()) {
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

    private String serialize(Object m) {
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
    private Map<String, Object> deserialize(String ab) {
        if (ab == null) {
            return null;
        }

        try {
            return objectMapper.readValue(ab, Map.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private TriggerEntry toEntity(Record10<UUID, UUID, String, UUID, String, String, String[], String, String, String> item) {
        List<String> activeProfiles = null;
        if (item.value7() != null) {
            activeProfiles = Arrays.asList(item.value7());
        }

        return new TriggerEntry(item.value1(), item.value2(), item.value3(), item.value4(), item.value5(), item.value6(),
                activeProfiles, deserialize(item.value8()), deserialize(item.value9()), deserialize(item.value10()));
    }
}
