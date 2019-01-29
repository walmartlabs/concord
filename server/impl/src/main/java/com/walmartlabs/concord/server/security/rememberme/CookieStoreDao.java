package com.walmartlabs.concord.server.security.rememberme;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;

import java.sql.Timestamp;

import static com.walmartlabs.concord.server.jooq.tables.RememberMeCookies.REMEMBER_ME_COOKIES;

@Named
public class CookieStoreDao extends AbstractDao {

    @Inject
    public CookieStoreDao(@Named("app") Configuration cfg) {
        super(cfg);
    }

    public void insert(byte[] cookieHash, byte[] data) {
        tx(tx -> tx.insertInto(REMEMBER_ME_COOKIES)
                .columns(REMEMBER_ME_COOKIES.COOKIE_HASH, REMEMBER_ME_COOKIES.SESSION_DATA)
                .values(cookieHash, data)
                .execute());
    }

    public byte[] get(byte[] cookieHash) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(REMEMBER_ME_COOKIES.SESSION_DATA)
                    .from(REMEMBER_ME_COOKIES)
                    .where(REMEMBER_ME_COOKIES.COOKIE_HASH.eq(cookieHash))
                    .fetchOne(REMEMBER_ME_COOKIES.SESSION_DATA);
        }
    }

    public int delete(Timestamp olderThan) {
        return txResult(tx -> tx.deleteFrom(REMEMBER_ME_COOKIES)
                .where(REMEMBER_ME_COOKIES.CREATED_AT.lessOrEqual(olderThan))
                .execute());
    }
}
