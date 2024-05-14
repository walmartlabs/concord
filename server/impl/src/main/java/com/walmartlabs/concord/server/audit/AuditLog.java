package com.walmartlabs.concord.server.audit;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.server.Listeners;
import com.walmartlabs.concord.server.RequestUtils;
import com.walmartlabs.concord.server.cfg.AuditConfiguration;
import com.walmartlabs.concord.server.org.project.DiffUtils;
import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import com.walmartlabs.concord.server.sdk.audit.AuditEvent;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.security.sessionkey.SessionKeyPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AuditLog {

    private static final Logger log = LoggerFactory.getLogger(AuditLog.class);

    private static final ThreadLocal<ActionSourceParameters> threadLocalActionSource = new ThreadLocal<>();

    /**
     * Any audit log calls done inside the provided {@link Runnable} will have
     * the specified {@link ActionSource} and its parameters added automatically.
     */
    public static void withActionSource(ActionSource source, Map<String, Object> params, Runnable runnable) {
        try {
            threadLocalActionSource.set(new ActionSourceParameters(source, params));
            runnable.run();
        } finally {
            threadLocalActionSource.set(null);
        }
    }

    private final AuditConfiguration cfg;
    private final AuditDao auditDao;
    private final Listeners listeners;

    @Inject
    public AuditLog(AuditConfiguration cfg, AuditDao auditDao, Listeners listeners) {
        this.cfg = cfg;
        this.auditDao = auditDao;
        this.listeners = listeners;
    }

    public EntryBuilder add(AuditObject object, AuditAction action) {
        return new EntryBuilder(object, action);
    }

    public class EntryBuilder {

        private final AuditObject object;
        private final AuditAction action;
        private final Map<String, Object> details;
        private Map<String, Object> changes;

        private UUID userId;

        private EntryBuilder(AuditObject object, AuditAction action) {
            this.object = object;
            this.action = action;
            this.details = new HashMap<>();
            this.changes = new HashMap<>();
        }

        public EntryBuilder userId(UUID userId) {
            this.userId = userId;
            return this;
        }

        public EntryBuilder field(String k, Object v) {
            if (v == null) {
                return this;
            }

            this.details.put(k, v);
            return this;
        }

        public EntryBuilder changes(Object prevEntry, Object newEntry) {
            if (prevEntry == null && newEntry == null) {
                return this;
            }

            this.changes = DiffUtils.compare(prevEntry, newEntry);
            return this;
        }

        public EntryBuilder actionSource(ActionSource source) {
            return actionSource(source, Collections.emptyMap());
        }

        public EntryBuilder actionSource(ActionSource source, Map<String, Object> params) {
            Map<String, Object> m = new HashMap<>(params != null ? params : Collections.emptyMap());
            m.put("type", source);
            this.details.put("actionSource", m);
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

            // save the thread-local action source
            ActionSourceParameters actionSourceParams = threadLocalActionSource.get();
            if (actionSourceParams != null) {
                actionSource(actionSourceParams.source, actionSourceParams.params);
            } else if (!details.containsKey("actionSource")) {
                // automatically fill-in actionSource if it wasn't defined before
                SessionKeyPrincipal sessionKey = SessionKeyPrincipal.getCurrent();
                if (sessionKey != null) {
                    // the request was made from within a process
                    PartialProcessKey processKey = sessionKey.getProcessKey();
                    actionSource(ActionSource.PROCESS, Collections.singletonMap("instanceId", processKey.getInstanceId()));
                } else if (RequestUtils.isItAUIRequest()) {
                    // the request was sent by the UI
                    actionSource(ActionSource.UI);
                } else {
                    // the request was made using the API
                    actionSource(ActionSource.API_REQUEST);
                }
            }

            details.put("requestId", RequestUtils.getRequestId());
            details.put("requestIp", RequestUtils.getRequestIp());

            if (changes != null && !changes.isEmpty()) {
                details.put("changes", changes);
            }

            AuditEvent ev = auditDao.insert(userId, object, action, details);
            listeners.onAuditEvent(ev);
        }
    }

    public static class ActionSourceParameters {

        private final ActionSource source;
        private final Map<String, Object> params;

        private ActionSourceParameters(ActionSource source, Map<String, Object> params) {
            this.source = source;
            this.params = params;
        }
    }
}
