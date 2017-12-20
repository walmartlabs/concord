package com.walmartlabs.concord.db;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

import org.jooq.Configuration;
import org.jooq.SQLDialect;
import org.jooq.conf.RenderNameStyle;
import org.jooq.conf.Settings;
import org.jooq.impl.DefaultConfiguration;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.sql.DataSource;

@Singleton
public class JooqConfigurationProvider implements Provider<Configuration> {

    private final DataSource dataSource;
    private final SQLDialect dialect;

    @Inject
    public JooqConfigurationProvider(DataSource dataSource, DatabaseConfiguration cfg) {
        this.dataSource = dataSource;
        this.dialect = cfg.getDialect();
    }

    @Override
    public Configuration get() {
        Settings settings = new Settings();
        settings.setRenderSchema(false);
        settings.setRenderCatalog(false);
        settings.setRenderNameStyle(RenderNameStyle.AS_IS);

        return new DefaultConfiguration()
                .set(settings)
                .set(dataSource)
                .set(dialect);
    }
}
