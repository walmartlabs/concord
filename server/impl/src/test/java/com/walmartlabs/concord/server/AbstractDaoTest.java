package com.walmartlabs.concord.server;

import com.walmartlabs.concord.common.db.DataSourceProvider;
import com.walmartlabs.concord.db.DatabaseChangeLogProviderImpl;
import com.walmartlabs.concord.server.cfg.DatabaseConfigurationProvider;
import org.jooq.Configuration;
import org.jooq.SQLDialect;
import org.jooq.impl.DefaultConfiguration;
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
        cfg = new DefaultConfiguration()
                .set(dataSource)
                .set(SQLDialect.H2);
    }

    @After
    public void closeDataSource() throws Exception {
        Method m = dataSource.getClass().getMethod("close");
        m.invoke(dataSource);
    }

    protected DataSource getDataSource() {
        return dataSource;
    }

    protected Configuration getConfiguration() {
        return cfg;
    }
}
