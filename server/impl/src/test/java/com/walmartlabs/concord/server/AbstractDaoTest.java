package com.walmartlabs.concord.server;

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
