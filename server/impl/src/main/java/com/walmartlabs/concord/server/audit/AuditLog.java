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

import com.walmartlabs.concord.server.RequestId;
import com.walmartlabs.concord.server.cfg.AuditConfiguration;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.security.sessionkey.SessionKeyPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Named
public class AuditLog {

    private static final Logger log = LoggerFactory.getLogger(AuditLog.class);

    private final AuditConfiguration cfg;
    private final AuditDao auditDao;

    @Inject
    public AuditLog(AuditConfiguration cfg, AuditDao auditDao) {
        this.cfg = cfg;
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
            if (!cfg.isEnabled()) {
                return;
            }

            try {
                doLog();
            } catch (Exception e) {
                log.error("log -> error while inserting an audit log entry: {}", e.getMessage(), e);
                throw e;
            }
        }

        private void doLog() {
            if (userId == null) {
                UserPrincipal user = UserPrincipal.getCurrent();
                if (user != null) {
                    userId = user.getId();
                }
            }

            Map<String, Object> actionSource = new HashMap<>();
            SessionKeyPrincipal sessionKey = SessionKeyPrincipal.getCurrent();
            if (sessionKey != null) {
                // if the request was made from within a process
                actionSource.put("type", ActionSource.PROCESS);
                actionSource.put("instanceId", sessionKey.getProcessInstanceId());
            } else {
                // if the request was made using the API
                actionSource.put("type", ActionSource.API_REQUEST);
            }
            details.put("actionSource", actionSource);

            details.put("requestId", RequestId.get());

            auditDao.insert(userId, object, action, details);
        }
    }
}
