package com.walmartlabs.concord.server;

import com.walmartlabs.concord.common.db.DataSourceProvider;
import com.walmartlabs.concord.common.db.DatabaseConfiguration;
import com.walmartlabs.concord.common.db.JooqConfigurationProvider;
import com.walmartlabs.concord.db.DatabaseChangeLogProviderImpl;
import com.walmartlabs.concord.server.cfg.DatabaseConfigurationProvider;
import org.jooq.Configuration;
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
        DataSourceProvider dsp = new DataSourceProvider(new DatabaseConfigurationProvider().get(),
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

    protected Configuration getConfiguration() {
        return cfg;
    }
}
