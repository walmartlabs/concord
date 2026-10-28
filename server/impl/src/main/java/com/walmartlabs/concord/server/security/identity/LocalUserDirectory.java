package com.walmartlabs.concord.server.security.identity;

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

import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.sdk.security.DirectoryAmbiguousException;
import com.walmartlabs.concord.server.sdk.security.DirectoryEntry;
import com.walmartlabs.concord.server.sdk.security.DirectoryQuery;
import com.walmartlabs.concord.server.sdk.security.ExternalIdentityKey;
import com.walmartlabs.concord.server.sdk.security.UserDirectory;
import com.walmartlabs.concord.server.sdk.security.UserProfile;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Local identities are owned by accounts and use their UUID as subject. */
public class LocalUserDirectory implements UserDirectory {
    private final DSLContext db;

    @Inject
    public LocalUserDirectory(@MainDB Configuration configuration) {
        this.db = DSL.using(configuration);
    }

    @Override
    public String id() { return "local"; }

    @Override
    public boolean acceptsIdentity(ExternalIdentityKey identity) {
        if (identity == null || !id().equals(identity.providerId()) || !identity.authority().isEmpty()) {
            return false;
        }
        try {
            return UUID.fromString(identity.subject()).toString().equals(identity.subject());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public Optional<DirectoryEntry> find(DirectoryQuery query) {
        if (!"username".equals(query.attribute())) {
            throw new IllegalArgumentException("Unsupported local directory attribute: " + query.attribute());
        }
        Result<Record> rows = db.fetch("SELECT u.user_id, u.username, u.domain, u.display_name, u.user_email " +
                        "FROM users u JOIN user_identities i ON i.user_id = u.user_id " +
                        "WHERE i.provider_id = 'local' AND i.authority = '' AND i.is_active " +
                        "AND i.subject = cast(u.user_id as text) AND u.username = ? AND u.domain IS NOT DISTINCT FROM ?",
                query.value().toLowerCase(Locale.ROOT), query.domain() == null ? null : query.domain().toLowerCase(Locale.ROOT));
        if (rows.size() > 1) {
            throw new DirectoryAmbiguousException("Ambiguous local directory lookup");
        }
        return rows.isEmpty() ? Optional.empty() : Optional.of(entry(rows.get(0)));
    }

    @Override
    public Optional<UserProfile> get(ExternalIdentityKey identity) {
        if (!acceptsIdentity(identity)) {
            throw new IllegalArgumentException("Invalid local identity");
        }
        Record row = db.fetchOne("SELECT u.user_id, u.username, u.domain, u.display_name, u.user_email " +
                        "FROM users u JOIN user_identities i ON i.user_id = u.user_id " +
                        "WHERE u.user_id = ? AND i.provider_id = 'local' AND i.authority = '' " +
                        "AND i.subject = ? AND i.is_active", UUID.fromString(identity.subject()), identity.subject());
        return row == null ? Optional.empty() : Optional.of(entry(row).profile());
    }

    private static DirectoryEntry entry(Record row) {
        UUID userId = row.get("user_id", UUID.class);
        return new DirectoryEntry(new ExternalIdentityKey("local", "", userId.toString()),
                new UserProfile(row.get("username", String.class), row.get("domain", String.class),
                        row.get("display_name", String.class), row.get("user_email", String.class), Map.of()));
    }
}
