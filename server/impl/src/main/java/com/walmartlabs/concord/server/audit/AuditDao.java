package com.walmartlabs.concord.server.audit;

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
import com.walmartlabs.concord.server.jooq.tables.AuditLog;
import com.walmartlabs.concord.server.jooq.tables.Users;
import com.walmartlabs.concord.server.org.EntityOwner;
import com.walmartlabs.concord.server.user.UserType;
import org.jooq.Configuration;
import org.jooq.JSONB;
import org.jooq.Record9;
import org.jooq.SelectOnConditionStep;

import javax.inject.Inject;
import javax.inject.Named;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.AuditLog.AUDIT_LOG;
import static com.walmartlabs.concord.server.jooq.tables.Users.USERS;

@Named
public class AuditDao extends AbstractDao {

    private final ConcordObjectMapper objectMapper;

    @Inject
    public AuditDao(@MainDB Configuration cfg,
                    ConcordObjectMapper objectMapper) {
        super(cfg);

        this.objectMapper = objectMapper;
    }

    public void insert(UUID userId, AuditObject object, AuditAction action, Object details) {
        tx(tx -> tx.insertInto(AUDIT_LOG)
                .columns(AUDIT_LOG.USER_ID,
                        AUDIT_LOG.ENTRY_OBJECT,
                        AUDIT_LOG.ENTRY_ACTION,
                        AUDIT_LOG.ENTRY_DETAILS)
                .values(userId,
                        object.toString(),
                        action.toString(),
                        objectMapper.toJSONB(details))
                .execute());
    }

    public List<AuditLogEntry> list(AuditLogFilter filter) {
        return txResult(tx -> {
            AuditLog l = AUDIT_LOG.as("l");
            Users u = USERS.as("u");

            SelectOnConditionStep<Record9<OffsetDateTime, String, String, JSONB, UUID, String, String, String, String>> q = tx.select(l.ENTRY_DATE,
                    l.ENTRY_ACTION,
                    l.ENTRY_OBJECT,
                    l.ENTRY_DETAILS,
                    u.USER_ID,
                    u.USERNAME,
                    u.DOMAIN,
                    u.USER_TYPE,
                    u.DISPLAY_NAME)
                    .from(l)
                    .leftJoin(u).on(u.USER_ID.eq(l.USER_ID));

            AuditObject object = filter.object();
            if (object != null) {
                q.where(l.ENTRY_OBJECT.eq(object.name()));
            }

            AuditAction action = filter.action();
            if (action != null) {
                q.where(l.ENTRY_ACTION.eq(action.name()));
            }

            UUID userId = filter.userId();
            if (userId != null) {
                q.where(l.USER_ID.eq(userId));
            }

            Map<String, Object> details = filter.details();
            if (details != null) {
                q.where(PgUtils.jsonbContains(l.ENTRY_DETAILS, objectMapper.toJSONB(details)));
            }

            OffsetDateTime after = filter.after();
            if (after != null) {
                q.where(l.ENTRY_DATE.greaterThan(after));
            }

            OffsetDateTime before = filter.before();
            if (before != null) {
                q.where(l.ENTRY_DATE.lessThan(before));
            }

            Integer limit = filter.limit();
            if (limit != null) {
                q.limit(limit);
            }

            Integer offset = filter.offset();
            if (offset != null) {
                q.offset(offset);
            }

            q.orderBy(l.ENTRY_DATE.desc(), l.ENTRY_SEQ.desc());

            return q.fetch(this::toEntry);
        });
    }

    private AuditLogEntry toEntry(Record9<OffsetDateTime, String, String, JSONB, UUID, String, String, String, String> r) {
        ImmutableAuditLogEntry.Builder b = AuditLogEntry.builder()
                .entryDate(r.get(0, OffsetDateTime.class))
                .action(AuditAction.valueOf(r.get(1, String.class)))
                .object(AuditObject.valueOf(r.get(2, String.class)))
                .details(objectMapper.fromJSONB(r.get(3, JSONB.class)));

        if (r.get(4) != null) {
            b.user(EntityOwner.builder()
                    .id(r.get(4, UUID.class))
                    .username(r.get(5, String.class))
                    .userDomain(r.get(6, String.class))
                    .userType(UserType.valueOf(r.get(7, String.class)))
                    .displayName(r.get(8, String.class))
                    .build());
        }

        return b.build();
    }
}
