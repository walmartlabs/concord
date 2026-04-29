package com.walmartlabs.concord.server.security.apikey.loader;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2021 Walmart Inc.
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
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.ApiKeys.API_KEYS;
import static com.walmartlabs.concord.server.jooq.tables.Users.USERS;
import static com.walmartlabs.concord.server.security.apikey.ApiKeyUtils.hash;
import static java.util.Objects.requireNonNull;

public class ApiKeyLoaderDao extends AbstractDao {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyLoaderDao.class);

    @Inject
    public ApiKeyLoaderDao(@MainDB Configuration cfg) {
        super(cfg);
    }

    public void upsert(List<ApiKeyEntry> entries) {
        tx(tx -> {
            for (ApiKeyEntry entry : entries) {
                String username = requireNonNull(entry.username(), "'username' is null");
                String keyName = requireNonNull(entry.keyName(), "'keyName' is null");
                String value = requireNonNull(entry.value(), "'value' is null");
                OffsetDateTime expiredAt = entry.expiredAt();

                UUID userId = getUserId(tx, username);
                if (userId == null) {
                    log.warn("User not found '{}', skipping...", username);
                    continue;
                }

                log.info("Updating API key '{}' for user '{}'...", keyName, username);

                UUID keyId = getKeyId(tx, userId, keyName);
                if (keyId != null) {
                    deleteKey(tx, keyId);
                }

                insertKey(tx, userId, keyName, value, expiredAt);
            }
        });
    }

    private static UUID getUserId(DSLContext tx, String username) {
        List<UUID> uuids = tx.select(USERS.USER_ID).from(USERS)
                .where(USERS.USERNAME.eq(username))
                .fetch(USERS.USER_ID);

        if (uuids.isEmpty()) {
            return null;
        }

        if (uuids.size() != 1) {
            throw new IllegalStateException("Non-unique username: " + username);
        }

        return uuids.get(0);
    }

    private static UUID getKeyId(DSLContext tx, UUID userId, String keyName) {
        return tx.select(API_KEYS.KEY_ID).from(API_KEYS)
                .where(API_KEYS.USER_ID.eq(userId)
                        .and(API_KEYS.KEY_NAME.eq(keyName)))
                .fetchOne(API_KEYS.KEY_ID);
    }

    private static void deleteKey(DSLContext tx, UUID keyId) {
        tx.deleteFrom(API_KEYS).where(API_KEYS.KEY_ID.eq(keyId)).execute();
    }

    private static void insertKey(DSLContext tx, UUID userId, String keyName, String value, OffsetDateTime expiredAt) {
        tx.insertInto(API_KEYS)
                .columns(API_KEYS.USER_ID, API_KEYS.API_KEY, API_KEYS.KEY_NAME, API_KEYS.EXPIRED_AT)
                .values(userId, hash(value), keyName, expiredAt)
                .execute();
    }
}
