package com.walmartlabs.concord.server.triggers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.api.trigger.TriggerEntry;
import com.walmartlabs.concord.server.jooq.tables.Triggers;
import org.jooq.*;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
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
    public TriggersDao(Configuration cfg) {
        super(cfg);

        this.objectMapper = new ObjectMapper();
    }

    public TriggerEntry get(UUID id) {
        Triggers t = TRIGGERS.as("t");

        Field<String> repositoryNameField = select(REPOSITORIES.REPO_NAME).from(REPOSITORIES).where(REPOSITORIES.REPO_ID.eq(t.REPO_ID)).asField();
        Field<String> projectNameField = select(PROJECTS.PROJECT_NAME).from(PROJECTS).where(PROJECTS.PROJECT_ID.eq(t.PROJECT_ID)).asField();

        try (DSLContext tx = DSL.using(cfg)) {
            return tx
                    .select(t.TRIGGER_ID,
                            t.PROJECT_ID, projectNameField,
                            t.REPO_ID, repositoryNameField,
                            t.EVENT_NAME, t.ENTRY_POINT,
                            t.ARGUMENTS.cast(String.class), t.CONDITIONS.cast(String.class))
                    .from(t)
                    .where(t.TRIGGER_ID.eq(id))
                    .fetchOne(this::toEntity);
        }
    }

    public UUID insert(UUID projectId, UUID repositoryId, String eventName, String entryPoint, Map<String, Object> args, Map<String, Object> conditions) {
        return txResult(tx -> insert(tx, projectId, repositoryId, eventName, entryPoint, args, conditions));
    }

    public UUID insert(DSLContext tx, UUID projectId, UUID repositoryId, String eventName, String entryPoint, Map<String, Object> args, Map<String, Object> conditions) {
        return tx.insertInto(TRIGGERS)
                .columns(TRIGGERS.PROJECT_ID, TRIGGERS.REPO_ID, TRIGGERS.EVENT_NAME, TRIGGERS.ENTRY_POINT, TRIGGERS.ARGUMENTS, TRIGGERS.CONDITIONS)
                .values(projectId, repositoryId, eventName, entryPoint, field("?::jsonb", serialize(args)), field("?::jsonb", serialize(conditions)))
                .returning(TRIGGERS.TRIGGER_ID)
                .fetchOne()
                .getTriggerId();
    }

    public void update(UUID id, UUID projectId, UUID repositoryId, String eventName, String entryPoint, Map<String, Object> args, Map<String, Object> conditions) {
        tx(tx -> update(tx, id, projectId, repositoryId, eventName, entryPoint, args, conditions));
    }

    private void update(DSLContext tx, UUID id, UUID projectId, UUID repositoryId, String eventName, String entryPoint, Map<String, Object> args, Map<String, Object> conditions) {
        tx.update(TRIGGERS)
                .set(TRIGGERS.PROJECT_ID, projectId)
                .set(TRIGGERS.REPO_ID, repositoryId)
                .set(TRIGGERS.EVENT_NAME, eventName)
                .set(TRIGGERS.ENTRY_POINT, entryPoint)
                .set(TRIGGERS.ARGUMENTS, field("?::jsonb", String.class, serialize(args)))
                .set(TRIGGERS.CONDITIONS, field("?::jsonb", String.class, serialize(conditions)))
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

    public List<TriggerEntry> list(String eventName, Map<String, String> conditions) {
        try (DSLContext tx = DSL.using(cfg)) {
            return list(tx, eventName, conditions);
        }
    }

    public List<TriggerEntry> list(DSLContext tx, String eventName, Map<String, String> conditions) {
        Triggers t = TRIGGERS.as("t");

        Field<String> repositoryNameField = select(REPOSITORIES.REPO_NAME).from(REPOSITORIES).where(REPOSITORIES.REPO_ID.eq(t.REPO_ID)).asField();
        Field<String> projectNameField = select(PROJECTS.PROJECT_NAME).from(PROJECTS).where(PROJECTS.PROJECT_ID.eq(t.PROJECT_ID)).asField();

        return tx.select(t.TRIGGER_ID,
                         t.PROJECT_ID, projectNameField,
                         t.REPO_ID, repositoryNameField,
                         t.EVENT_NAME, t.ENTRY_POINT,
                         t.ARGUMENTS.cast(String.class), t.CONDITIONS.cast(String.class))
                .from(t)
                .where(buildConditionClause(t, eventName, conditions))
                .fetch(this::toEntity);
    }

    private Condition buildConditionClause(Triggers t, String eventName, Map<String, String> conditions) {
        Condition result = t.EVENT_NAME.eq(eventName);
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
            throw Throwables.propagate(e);
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
            throw Throwables.propagate(e);
        }
    }

    private TriggerEntry toEntity(Record9<UUID, UUID, String, UUID, String, String, String, String, String> item) {
        return new TriggerEntry(item.value1(), item.value2(), item.value3(), item.value4(), item.value5(), item.value6(), item.value7(),
                deserialize(item.value8()), deserialize(item.value9()));
    }
}
