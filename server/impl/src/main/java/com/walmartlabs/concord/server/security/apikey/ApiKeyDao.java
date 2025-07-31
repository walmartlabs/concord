package com.walmartlabs.concord.server.security.apikey;

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
import com.walmartlabs.concord.server.UuidGenerator;
import org.jooq.Configuration;
import org.jooq.Record4;

import javax.inject.Inject;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.ApiKeys.API_KEYS;
import static com.walmartlabs.concord.server.security.apikey.ApiKeyUtils.hash;
import static java.util.Objects.requireNonNull;
import static org.jooq.impl.DSL.currentOffsetDateTime;
import static org.jooq.impl.DSL.selectFrom;

public class ApiKeyDao extends AbstractDao {

    private final SecureRandom rnd;
    private final UuidGenerator uuidGenerator;

    @Inject
    public ApiKeyDao(@MainDB Configuration cfg, SecureRandom rnd, UuidGenerator uuidGenerator) {
        super(cfg);
        this.rnd = requireNonNull(rnd);
        this.uuidGenerator = requireNonNull(uuidGenerator);
    }

    public String newApiKey() {
        byte[] ab = new byte[16];
        rnd.nextBytes(ab);

        Encoder e = Base64.getEncoder().withoutPadding();
        return e.encodeToString(ab);
    }

    public UUID getId(UUID userId, String keyName) {
        return dsl().select(API_KEYS.KEY_ID)
                .from(API_KEYS)
                .where(API_KEYS.USER_ID.eq(userId)
                        .and(API_KEYS.KEY_NAME.eq(keyName)))
                .fetchOne(API_KEYS.KEY_ID);
    }

    public List<ApiKeyEntry> list(UUID userId) {
        return dsl().select(
                API_KEYS.KEY_ID,
                API_KEYS.USER_ID,
                API_KEYS.KEY_NAME,
                API_KEYS.EXPIRED_AT)
                .from(API_KEYS)
                .where(API_KEYS.USER_ID.eq(userId))
                .orderBy(API_KEYS.KEY_NAME)
                .fetch(ApiKeyDao::toEntry);
    }

    public UUID insert(UUID userId, String key, String name, OffsetDateTime expiredAt) {
        UUID keyId = uuidGenerator.generate();
        return txResult(tx -> tx.insertInto(API_KEYS)
                .columns(API_KEYS.KEY_ID, API_KEYS.USER_ID, API_KEYS.API_KEY, API_KEYS.KEY_NAME, API_KEYS.EXPIRED_AT)
                .values(keyId, userId, hash(key), name, expiredAt)
                .returning(API_KEYS.KEY_ID)
                .fetchOne()
                .getKeyId());
    }

    public void update(UUID keyId, String key, OffsetDateTime expiredAt) {
        tx(tx -> tx.update(API_KEYS)
                .set(API_KEYS.API_KEY, hash(key))
                .set(API_KEYS.EXPIRED_AT, expiredAt)
                .where(API_KEYS.KEY_ID.eq(keyId)));
    }

    public void delete(UUID id) {
        tx(tx -> tx.deleteFrom(API_KEYS)
                .where(API_KEYS.KEY_ID.eq(id))
                .execute());
    }

    public UUID getUserId(UUID id) {
        return dsl().select(API_KEYS.USER_ID)
                .from(API_KEYS)
                .where(API_KEYS.KEY_ID.eq(id))
                .fetchOne(API_KEYS.USER_ID);
    }

    public ApiKeyEntry find(String key) {
        return dsl().select(API_KEYS.KEY_ID, API_KEYS.USER_ID, API_KEYS.KEY_NAME, API_KEYS.EXPIRED_AT)
                .from(API_KEYS)
                .where(API_KEYS.API_KEY.eq(hash(key))
                        .and(API_KEYS.EXPIRED_AT.isNull()
                                .or(API_KEYS.EXPIRED_AT.greaterThan(currentOffsetDateTime()))))
                .fetchOne(ApiKeyDao::toEntry);
    }

    public int count(UUID userId) {
        return dsl().fetchCount(selectFrom(API_KEYS).where(API_KEYS.USER_ID.eq(userId)));
    }

    private static ApiKeyEntry toEntry(Record4<UUID, UUID, String, OffsetDateTime> r) {
        return new ApiKeyEntry(r.value1(), r.value2(), r.value3(), r.value4());
    }
}
