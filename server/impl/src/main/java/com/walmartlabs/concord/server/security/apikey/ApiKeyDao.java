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


import com.google.common.base.Throwables;
import com.walmartlabs.concord.db.AbstractDao;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.ApiKeys.API_KEYS;

@Named
public class ApiKeyDao extends AbstractDao {

    private final SecureRandom rnd;

    @Inject
    public ApiKeyDao(Configuration cfg, SecureRandom rnd) {
        super(cfg);
        this.rnd = rnd;
    }

    public String newApiKey() {
        byte[] ab = new byte[16];
        rnd.nextBytes(ab);

        Encoder e = Base64.getEncoder().withoutPadding();
        return e.encodeToString(ab);
    }

    public UUID insert(UUID userId, String key) {
        return txResult(tx -> tx.insertInto(API_KEYS)
                .columns(API_KEYS.USER_ID, API_KEYS.API_KEY)
                .values(userId, hash(key))
                .returning(API_KEYS.KEY_ID)
                .fetchOne()
                .getKeyId());
    }

    public void delete(UUID id) {
        tx(tx -> tx.deleteFrom(API_KEYS)
                .where(API_KEYS.KEY_ID.eq(id))
                .execute());
    }

    public UUID getUserId(UUID id) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(API_KEYS.USER_ID)
                    .from(API_KEYS)
                    .where(API_KEYS.KEY_ID.eq(id))
                    .fetchOne(API_KEYS.USER_ID);
        }
    }

    public UUID findUserId(String key) {
        try (DSLContext tx = DSL.using(cfg)) {
            UUID id = tx.select(API_KEYS.USER_ID)
                    .from(API_KEYS)
                    .where(API_KEYS.API_KEY.eq(hash(key)))
                    .fetchOne(API_KEYS.USER_ID);

            if (id == null) {
                return null;
            }

            return id;
        }
    }

    private static String hash(String s) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw Throwables.propagate(e);
        }

        byte[] ab = Base64.getDecoder().decode(s);
        ab = md.digest(ab);

        return Base64.getEncoder().withoutPadding().encodeToString(ab);
    }
}
