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
import com.walmartlabs.concord.server.ConcordObjectMapper;
import org.jooq.Configuration;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.AuditLog.AUDIT_LOG;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.value;

@Named
public class AuditDao extends AbstractDao {

    private final ConcordObjectMapper objectMapper;

    @Inject
    public AuditDao(@Named("app") Configuration cfg,
                    ConcordObjectMapper objectMapper) {
        super(cfg);

        this.objectMapper = objectMapper;
    }

    public void insert(UUID userId, AuditObject object, AuditAction action, Object details) {
        tx(tx -> insert(tx, userId, object, action, details));
    }

    public void insert(DSLContext tx, UUID userId, AuditObject object, AuditAction action, Object details) {
        tx.insertInto(AUDIT_LOG)
                .columns(AUDIT_LOG.USER_ID,
                        AUDIT_LOG.ENTRY_OBJECT,
                        AUDIT_LOG.ENTRY_ACTION,
                        AUDIT_LOG.ENTRY_DETAILS)
                .values(value(userId),
                        value(object.toString()),
                        value(action.toString()),
                        field("?::jsonb", objectMapper.serialize(details)))
                .execute();
    }
}
