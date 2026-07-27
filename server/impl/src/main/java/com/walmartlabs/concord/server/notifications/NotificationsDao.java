package com.walmartlabs.concord.server.notifications;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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
import com.walmartlabs.concord.server.UuidGenerator;
import com.walmartlabs.concord.server.jooq.tables.Notifications;
import com.walmartlabs.concord.server.jooq.tables.records.NotificationsRecord;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Record12;
import org.jooq.SelectWhereStep;
import org.jooq.UpdateQuery;

import javax.inject.Inject;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.Notifications.NOTIFICATIONS;
import static java.util.Objects.requireNonNull;

public class NotificationsDao extends AbstractDao {

    private final UuidGenerator uuidGenerator;

    @Inject
    public NotificationsDao(@MainDB Configuration cfg,
                            UuidGenerator uuidGenerator) {
        super(cfg);
        this.uuidGenerator = requireNonNull(uuidGenerator);
    }

    @Override
    protected void tx(Tx t) {
        super.tx(t);
    }

    @Override
    public <T> T txResult(TxResult<T> t) {
        return super.txResult(t);
    }

    public UUID insert(UUID userId, UUID orgId, UUID projectId, UUID repoId,
                       String summary, String body, String actionLink, boolean triggerEmail) {
        return txResult(tx -> insert(tx, userId, orgId, projectId, repoId, summary, body, actionLink, triggerEmail));
    }

    public UUID insert(DSLContext tx, UUID userId, UUID orgId, UUID projectId, UUID repoId,
                       String summary, String body, String actionLink, boolean triggerEmail) {
        UUID id = uuidGenerator.generate();

        OffsetDateTime now = OffsetDateTime.now();

        return tx.insertInto(NOTIFICATIONS)
                .columns(NOTIFICATIONS.ID,
                        NOTIFICATIONS.USER_ID,
                        NOTIFICATIONS.ORG_ID,
                        NOTIFICATIONS.PROJECT_ID,
                        NOTIFICATIONS.REPO_ID,
                        NOTIFICATIONS.SUMMARY,
                        NOTIFICATIONS.BODY,
                        NOTIFICATIONS.ACTION_LINK,
                        NOTIFICATIONS.TRIGGER_EMAIL,
                        NOTIFICATIONS.CREATED_AT)
                .values(id, userId, orgId, projectId, repoId, summary, body, actionLink, triggerEmail, now)
                .returning()
                .fetchOne()
                .getId();
    }

    public void update(UUID id, String summary, String body, String actionLink,
                       Boolean triggerEmail, OffsetDateTime dismissedTimestamp, UUID dismissedBy) {
        tx(tx -> update(tx, id, summary, body, actionLink, triggerEmail, dismissedTimestamp, dismissedBy));
    }

    public void update(DSLContext tx, UUID id, String summary, String body, String actionLink,
                       Boolean triggerEmail, OffsetDateTime dismissedTimestamp, UUID dismissedBy) {
        UpdateQuery<NotificationsRecord> q = tx.updateQuery(NOTIFICATIONS);

        if (summary != null) {
            q.addValue(NOTIFICATIONS.SUMMARY, summary);
        }
        if (body != null) {
            q.addValue(NOTIFICATIONS.BODY, body);
        }
        if (actionLink != null) {
            q.addValue(NOTIFICATIONS.ACTION_LINK, actionLink);
        }
        if (triggerEmail != null) {
            q.addValue(NOTIFICATIONS.TRIGGER_EMAIL, triggerEmail);
        }
        if (dismissedTimestamp != null) {
            q.addValue(NOTIFICATIONS.DISMISSED_TIMESTAMP, dismissedTimestamp);
        }
        if (dismissedBy != null) {
            q.addValue(NOTIFICATIONS.DISMISSED_BY, dismissedBy);
        }

        q.addConditions(NOTIFICATIONS.ID.eq(id));
        q.execute();
    }

    public void delete(UUID id) {
        tx(tx -> tx.deleteFrom(NOTIFICATIONS)
                .where(NOTIFICATIONS.ID.eq(id))
                .execute());
    }

    public List<NotificationEntry> list(NotificationOwnerKind ownerKind, UUID ownerId, int offset, int limit) {
        Notifications n = NOTIFICATIONS.as("n");

        SelectWhereStep<Record12<UUID, UUID, UUID, UUID, UUID, String, String, String, Boolean, OffsetDateTime, UUID, OffsetDateTime>> q =
                dsl().select(n.ID, n.USER_ID, n.ORG_ID, n.PROJECT_ID, n.REPO_ID,
                                n.SUMMARY, n.BODY, n.ACTION_LINK, n.TRIGGER_EMAIL,
                                n.DISMISSED_TIMESTAMP, n.DISMISSED_BY, n.CREATED_AT)
                        .from(n);

        if (ownerKind != null && ownerId != null) {
            switch (ownerKind) {
                case USER -> q.where(n.USER_ID.eq(ownerId));
                case ORG -> q.where(n.ORG_ID.eq(ownerId));
                case PROJECT -> q.where(n.PROJECT_ID.eq(ownerId));
                case REPOSITORY -> q.where(n.REPO_ID.eq(ownerId));
            }
        }

        if (offset > 0) {
            q.offset(offset);
        }

        if (limit > 0) {
            q.limit(limit);
        }

        return q.orderBy(n.ID)
                .fetch(this::toEntry);
    }

    public NotificationEntry get(UUID id) {
        return get(dsl(), id);
    }

    public NotificationEntry get(DSLContext tx, UUID id) {
        Notifications n = NOTIFICATIONS.as("n");

        return tx.select(n.ID, n.USER_ID, n.ORG_ID, n.PROJECT_ID, n.REPO_ID,
                        n.SUMMARY, n.BODY, n.ACTION_LINK, n.TRIGGER_EMAIL,
                        n.DISMISSED_TIMESTAMP, n.DISMISSED_BY, n.CREATED_AT)
                .from(n)
                .where(n.ID.eq(id))
                .fetchOne(this::toEntry);
    }

    private NotificationEntry toEntry(Record12<UUID, UUID, UUID, UUID, UUID, String, String, String, Boolean, OffsetDateTime, UUID, OffsetDateTime> r) {
        boolean triggerEmail = r.value9();
        return new NotificationEntry(
                r.value1(),   // id
                r.value2(),   // userId
                r.value3(),   // orgId
                r.value4(),   // projectId
                r.value5(),   // repoId
                r.value6(),   // summary
                r.value7(),   // body
                r.value8(),   // actionLink
                triggerEmail,
                r.value12(),  // createdAt
                r.value10(),  // dismissedTimestamp
                r.value11()   // dismissedBy
        );
    }
}
