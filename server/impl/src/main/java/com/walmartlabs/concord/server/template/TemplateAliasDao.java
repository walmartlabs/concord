package com.walmartlabs.concord.server.template;

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
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Record2;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;

import static com.walmartlabs.concord.server.jooq.tables.TemplateAliases.TEMPLATE_ALIASES;

public class TemplateAliasDao extends AbstractDao {

    @Inject
    public TemplateAliasDao(@MainDB Configuration cfg) {
        super(cfg);
    }

    public boolean exists(String alias) {
        DSLContext tx = dsl();
        return tx.fetchExists(tx.selectFrom(TEMPLATE_ALIASES)
                .where(TEMPLATE_ALIASES.TEMPLATE_ALIAS.eq(alias)));
    }

    public Optional<String> get(String alias) {
        return dsl().select(TEMPLATE_ALIASES.TEMPLATE_URL)
                .from(TEMPLATE_ALIASES)
                .where(TEMPLATE_ALIASES.TEMPLATE_ALIAS.eq(alias))
                .fetchOptional(TEMPLATE_ALIASES.TEMPLATE_URL);
    }

    public void insert(String alias, String url) {
        tx(tx -> insert(tx, alias, url));
    }

    public void insert(DSLContext tx, String alias, String url) {
        tx.insertInto(TEMPLATE_ALIASES)
                .columns(TEMPLATE_ALIASES.TEMPLATE_ALIAS, TEMPLATE_ALIASES.TEMPLATE_URL)
                .values(alias, url)
                .execute();
    }

    public List<TemplateAliasEntry> list() {
        return dsl().select(TEMPLATE_ALIASES.TEMPLATE_ALIAS, TEMPLATE_ALIASES.TEMPLATE_URL)
                .from(TEMPLATE_ALIASES)
                .fetch(TemplateAliasDao::toEntry);
    }

    public void delete(String alias) {
        tx(tx -> delete(tx, alias));
    }

    public void delete(DSLContext tx, String alias) {
        tx.deleteFrom(TEMPLATE_ALIASES)
                .where(TEMPLATE_ALIASES.TEMPLATE_ALIAS.eq(alias))
                .execute();
    }

    private static TemplateAliasEntry toEntry(Record2<String, String> r) {
        return new TemplateAliasEntry(r.value1(), r.value2());
    }
}
