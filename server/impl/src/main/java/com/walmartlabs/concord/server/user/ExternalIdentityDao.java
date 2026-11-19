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

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.UuidGenerator;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.identity.ExternalIdentityRef;
import org.jooq.Configuration;
import org.jooq.Condition;

import javax.inject.Inject;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.Tables.EXTERNAL_APP_USERS;
import static com.walmartlabs.concord.server.jooq.Tables.EXTERNAL_IDENTITIES;
import static com.walmartlabs.concord.server.jooq.Tables.USERS;
import static org.jooq.impl.DSL.currentOffsetDateTime;

/**
 * Persistence only. Newly provisioned identities remain disabled and have no
 * default team or roles. Provisioning does not authorize identity use.
 */
public class ExternalIdentityDao extends AbstractDao {

    private final UuidGenerator uuidGenerator;

    @Inject
    public ExternalIdentityDao(@MainDB Configuration cfg, UuidGenerator uuidGenerator) {
        super(cfg);
        this.uuidGenerator = Objects.requireNonNull(uuidGenerator);
    }

    public Optional<UUID> getUserId(ExternalIdentityRef identity) {
        return dsl().select(EXTERNAL_IDENTITIES.USER_ID)
                .from(EXTERNAL_IDENTITIES)
                .where(matches(identity))
                .fetchOptional(EXTERNAL_IDENTITIES.USER_ID);
    }

    /**
     * Atomically provisions a disabled user and its authoritative binding.
     * Repeated or concurrent calls return the original user without modifying
     * its profile, disable state, roles or memberships.
     */
    public UUID getOrCreate(ExternalIdentityRef identity, String displayName, String email) {
        validate(identity);
        return txResult(tx -> {
            var existing = tx.select(EXTERNAL_IDENTITIES.USER_ID)
                    .from(EXTERNAL_IDENTITIES)
                    .where(matches(identity))
                    .fetchOne(EXTERNAL_IDENTITIES.USER_ID);
            if (existing != null) {
                return existing;
            }

            var userId = uuidGenerator.generate();
            tx.insertInto(USERS)
                    .set(USERS.USER_ID, userId)
                    .set(USERS.USERNAME, "external-" + userId)
                    .set(USERS.USER_TYPE, UserType.EXTERNAL.name())
                    .set(USERS.DISPLAY_NAME, displayName)
                    .set(USERS.USER_EMAIL, email)
                    .set(USERS.IS_DISABLED, true)
                    .set(USERS.DISABLED_DATE, currentOffsetDateTime())
                    .execute();

            var inserted = tx.insertInto(EXTERNAL_IDENTITIES)
                    .set(EXTERNAL_IDENTITIES.PROVIDER_ID, identity.providerId())
                    .set(EXTERNAL_IDENTITIES.AUTHORITY, identity.authority())
                    .set(EXTERNAL_IDENTITIES.SUBJECT_ID, identity.subjectId())
                    .set(EXTERNAL_IDENTITIES.USER_ID, userId)
                    .onConflict(EXTERNAL_IDENTITIES.PROVIDER_ID, EXTERNAL_IDENTITIES.AUTHORITY, EXTERNAL_IDENTITIES.SUBJECT_ID)
                    .doNothing()
                    .execute();
            if (inserted == 1) {
                return userId;
            }

            // A concurrent provisioner won. Remove only our provisional user.
            tx.deleteFrom(USERS).where(USERS.USER_ID.eq(userId)).execute();
            return tx.select(EXTERNAL_IDENTITIES.USER_ID)
                    .from(EXTERNAL_IDENTITIES)
                    .where(matches(identity))
                    .fetchSingle(EXTERNAL_IDENTITIES.USER_ID);
        });
    }

    /**
     * Claims an existing external mapping as an authoritative alias. Existing
     * aliases may be promoted for the same user, but never reassigned.
     */
    public void bindAlias(ExternalIdentityRef identity, String externalId) {
        validate(identity);
        if (externalId == null || externalId.isBlank() || externalId.length() > 512) {
            throw new IllegalArgumentException("External alias must contain between 1 and 512 characters");
        }
        tx(tx -> {
            var binding = tx.select(EXTERNAL_IDENTITIES.USER_ID)
                    .from(EXTERNAL_IDENTITIES)
                    .where(matches(identity))
                    .forUpdate()
                    .fetchOne();
            if (binding == null) {
                throw new ConcordApplicationException("External identity is not provisioned");
            }
            var userId = binding.value1();
            var updated = tx.insertInto(EXTERNAL_APP_USERS)
                    .set(EXTERNAL_APP_USERS.EXTERNAL_USER_ID, externalId)
                    .set(EXTERNAL_APP_USERS.USER_ID, userId)
                    .set(EXTERNAL_APP_USERS.IS_AUTHORITATIVE, true)
                    .onConflict(EXTERNAL_APP_USERS.EXTERNAL_USER_ID)
                    .doUpdate()
                    .set(EXTERNAL_APP_USERS.IS_AUTHORITATIVE, true)
                    .where(EXTERNAL_APP_USERS.USER_ID.eq(userId))
                    .execute();
            if (updated != 1) {
                throw new ConcordApplicationException("External alias is already bound to another user");
            }
        });
    }

    private static Condition matches(ExternalIdentityRef identity) {
        validate(identity);
        return EXTERNAL_IDENTITIES.PROVIDER_ID.eq(identity.providerId())
                .and(EXTERNAL_IDENTITIES.AUTHORITY.eq(identity.authority()))
                .and(EXTERNAL_IDENTITIES.SUBJECT_ID.eq(identity.subjectId()));
    }

    private static void validate(ExternalIdentityRef identity) {
        Objects.requireNonNull(identity, "identity");
        if (identity.providerId().length() > 128 || identity.authority().length() > 512
                || identity.subjectId().length() > 512) {
            throw new IllegalArgumentException("External identity exceeds persistence limits: providerId=128, authority=512, subjectId=512");
        }
    }
}
