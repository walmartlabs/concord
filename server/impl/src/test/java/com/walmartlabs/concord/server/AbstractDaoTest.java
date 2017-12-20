package com.walmartlabs.concord.server;

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

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.CommonDataSourceProvider;
import com.walmartlabs.concord.db.DatabaseConfiguration;
import com.walmartlabs.concord.db.JooqConfigurationProvider;
import com.walmartlabs.concord.db.DatabaseChangeLogProviderImpl;
import com.walmartlabs.concord.server.cfg.DatabaseConfigurationProvider;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.After;
import org.junit.Before;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.util.Collections;

public abstract class AbstractDaoTest {

    private DataSource dataSource;
    private Configuration cfg;

    @Before
    public void initDataSource() {
        CommonDataSourceProvider dsp = new CommonDataSourceProvider(new DatabaseConfigurationProvider().get(),
                Collections.singleton(new DatabaseChangeLogProviderImpl()));

        dataSource = dsp.get();

        DatabaseConfiguration dbCfg = new DatabaseConfigurationProvider().get();
        cfg = new JooqConfigurationProvider(dataSource, dbCfg).get();
    }

    @After
    public void closeDataSource() throws Exception {
        Method m = dataSource.getClass().getMethod("close");
        m.invoke(dataSource);
    }

    protected void tx(AbstractDao.Tx t) {
        try (DSLContext ctx = DSL.using(cfg)) {
            ctx.transaction(cfg -> {
                DSLContext tx = DSL.using(cfg);
                t.run(tx);
            });
        }
    }

    protected Configuration getConfiguration() {
        return cfg;
    }
}
