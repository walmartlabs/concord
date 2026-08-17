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
import com.walmartlabs.concord.server.jooq.tables.NotificationCommunicationHistory;
import com.walmartlabs.concord.server.jooq.tables.records.NotificationCommunicationHistoryRecord;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Record6;
import org.jooq.UpdateQuery;

import javax.inject.Inject;
import java.time.OffsetDateTime;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.NotificationCommunicationHistory.NOTIFICATION_COMMUNICATION_HISTORY;
import static java.util.Objects.requireNonNull;

public class CommunicationHistoryDao extends AbstractDao {

    private final UuidGenerator uuidGenerator;

    @Inject
    public CommunicationHistoryDao(@MainDB Configuration cfg,
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

    public UUID insert(UUID notificationId, String emailTo, String emailCc, String emailBcc, OffsetDateTime emailTimestamp) {
        return txResult(tx -> insert(tx, notificationId, emailTo, emailCc, emailBcc, emailTimestamp));
    }

    public UUID insert(DSLContext tx, UUID notificationId, String emailTo, String emailCc, String emailBcc, OffsetDateTime emailTimestamp) {
        UUID id = uuidGenerator.generate();

        return tx.insertInto(NOTIFICATION_COMMUNICATION_HISTORY)
                .columns(NOTIFICATION_COMMUNICATION_HISTORY.ID,
                        NOTIFICATION_COMMUNICATION_HISTORY.NOTIFICATION_ID,
                        NOTIFICATION_COMMUNICATION_HISTORY.EMAIL_TO,
                        NOTIFICATION_COMMUNICATION_HISTORY.EMAIL_CC,
                        NOTIFICATION_COMMUNICATION_HISTORY.EMAIL_BCC,
                        NOTIFICATION_COMMUNICATION_HISTORY.EMAIL_TIMESTAMP)
                .values(id, notificationId, emailTo, emailCc, emailBcc, emailTimestamp)
                .returning()
                .fetchOne()
                .getId();
    }

    public void update(UUID id, String emailTo, String emailCc, String emailBcc) {
        tx(tx -> update(tx, id, emailTo, emailCc, emailBcc));
    }

    public void update(DSLContext tx, UUID id, String emailTo, String emailCc, String emailBcc) {
        UpdateQuery<NotificationCommunicationHistoryRecord> q = tx.updateQuery(NOTIFICATION_COMMUNICATION_HISTORY);

        if (emailTo != null) {
            q.addValue(NOTIFICATION_COMMUNICATION_HISTORY.EMAIL_TO, emailTo);
        }
        if (emailCc != null) {
            q.addValue(NOTIFICATION_COMMUNICATION_HISTORY.EMAIL_CC, emailCc);
        }
        if (emailBcc != null) {
            q.addValue(NOTIFICATION_COMMUNICATION_HISTORY.EMAIL_BCC, emailBcc);
        }

        q.addConditions(NOTIFICATION_COMMUNICATION_HISTORY.ID.eq(id));
        q.execute();
    }

    public void delete(UUID id) {
        tx(tx -> tx.deleteFrom(NOTIFICATION_COMMUNICATION_HISTORY)
                .where(NOTIFICATION_COMMUNICATION_HISTORY.ID.eq(id))
                .execute());
    }

    public CommunicationHistoryEntry get(UUID id) {
        return get(dsl(), id);
    }

    public CommunicationHistoryEntry get(DSLContext tx, UUID id) {
        NotificationCommunicationHistory h = NOTIFICATION_COMMUNICATION_HISTORY.as("h");

        return tx.select(h.ID, h.NOTIFICATION_ID, h.EMAIL_TO, h.EMAIL_CC, h.EMAIL_BCC, h.EMAIL_TIMESTAMP)
                .from(h)
                .where(h.ID.eq(id))
                .fetchOne(this::toEntry);
    }

    private CommunicationHistoryEntry toEntry(Record6<UUID, UUID, String, String, String, OffsetDateTime> r) {
        return new CommunicationHistoryEntry(
                r.value1(),  // id
                r.value2(),  // notificationId
                r.value3(),  // emailTo
                r.value4(),  // emailCc
                r.value5(),  // emailBcc
                r.value6()   // emailTimestamp
        );
    }
}
