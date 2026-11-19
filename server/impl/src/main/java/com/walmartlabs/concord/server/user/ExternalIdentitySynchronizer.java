package com.walmartlabs.concord.server.user;

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

import com.walmartlabs.concord.server.audit.ActionSource;
import com.walmartlabs.concord.server.audit.AuditAction;
import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.audit.AuditObject;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.identity.ExternalIdentityException;
import com.walmartlabs.concord.server.sdk.identity.ExternalIdentityProvider;
import com.walmartlabs.concord.server.sdk.identity.ExternalIdentityRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Explicit refresh of an already provisioned identity. Does not enable users,
 * schedule background work or establish an execution security context.
 */
public class ExternalIdentitySynchronizer {

    private static final Logger log = LoggerFactory.getLogger(ExternalIdentitySynchronizer.class);

    private final ExternalIdentityDao dao;
    private final AuditLog auditLog;
    private final Map<String, ExternalIdentityProvider> providers;

    @Inject
    public ExternalIdentitySynchronizer(ExternalIdentityDao dao, AuditLog auditLog,
                                        Set<ExternalIdentityProvider> providers) {
        this.dao = dao;
        this.auditLog = auditLog;
        var indexed = new HashMap<String, ExternalIdentityProvider>();
        for (var provider : providers) {
            var id = provider.getId();
            if (id == null || id.isBlank() || id.length() > 128) {
                throw new ConcordApplicationException("External identity provider ID must contain between 1 and 128 characters");
            }
            if (indexed.putIfAbsent(id, provider) != null) {
                throw new ConcordApplicationException("Duplicate external identity provider ID: " + id);
            }
        }
        this.providers = Map.copyOf(indexed);
    }

    public void refresh(ExternalIdentityRef identity) throws ExternalIdentityException {
        var provider = providers.get(identity.providerId());
        if (provider == null) {
            throw new ExternalIdentityException("External identity provider is not registered: " + identity.providerId());
        }
        var token = dao.beginRefresh(identity);
        try {
            var snapshot = provider.fetch(identity);
            if (snapshot == null || !identity.equals(snapshot.identity())) {
                throw new ExternalIdentityException("External identity provider returned a missing or mismatched snapshot");
            }
            if (!dao.applySnapshot(snapshot, token)) {
                throw new ExternalIdentityException("External identity refresh was superseded or its binding was deleted");
            }
            auditLog.add(AuditObject.USER, AuditAction.UPDATE)
                    .actionSource(ActionSource.SYSTEM)
                    .field("action", "syncExternalIdentity")
                    .field("providerId", identity.providerId())
                    .field("authority", identity.authority())
                    .field("subjectId", identity.subjectId())
                    .field("userId", token.userId())
                    .field("generation", token.generation())
                    .field("status", snapshot.status())
                    .field("groupCount", snapshot.groups().size())
                    .log();
        } catch (ExternalIdentityException e) {
            log.warn("refresh ['{}'] -> failed", identity, e);
            throw e;
        }
    }
}
