package com.walmartlabs.concord.server.audit;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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

import com.walmartlabs.concord.server.security.UserPrincipal;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Named
public class AuditLog {

    private final AuditDao auditDao;

    @Inject
    public AuditLog(AuditDao auditDao) {
        this.auditDao = auditDao;
    }

    public EntryBuilder add(AuditObject object, AuditAction action) {
        return new EntryBuilder(object, action);
    }

    public class EntryBuilder {

        private final AuditObject object;
        private final AuditAction action;
        private final Map<String, Object> details;

        private UUID userId;

        private EntryBuilder(AuditObject object, AuditAction action) {
            this.object = object;
            this.action = action;
            this.details = new HashMap<>();
        }

        public EntryBuilder userId(UUID userId) {
            this.userId = userId;
            return this;
        }

        public EntryBuilder field(String k, Object v) {
            if (v == null) {
                return this;
            }

            details.put(k, v);
            return this;
        }

        public void log() {
            if (userId == null) {
                UserPrincipal user = UserPrincipal.getCurrent();
                if (user != null) {
                    userId = user.getId();
                }
            }

            auditDao.insert(userId, object, action, details);
        }
    }
}
