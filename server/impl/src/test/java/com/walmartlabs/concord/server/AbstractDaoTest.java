package com.walmartlabs.concord.server;

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

import com.codahale.metrics.MetricRegistry;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.DatabaseConfiguration;
import com.walmartlabs.concord.db.DatabaseModule;
import com.walmartlabs.concord.db.MainDBChangeLogProvider;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;

public abstract class AbstractDaoTest {

    private final boolean migrateDb;

    public AbstractDaoTest() {
        this(true);
    }

    public AbstractDaoTest(boolean migrateDb) {
        this.migrateDb = migrateDb;
    }

    private DataSource dataSource;
    private Configuration cfg;

    @BeforeEach
    public void initDataSource() {
        DatabaseConfiguration cfg = new DatabaseConfigurationImpl("jdbc:postgresql://localhost:5432/postgres", "postgres", "q1", 3);

        DatabaseModule db = new DatabaseModule(migrateDb);
        this.dataSource = db.appDataSource(cfg, new MetricRegistry(), Collections.singleton(new MainDBChangeLogProvider()));

        this.cfg = db.appJooqConfiguration(this.dataSource);
    }

    @AfterEach
    public void closeDataSource() throws Exception {
        Method m = dataSource.getClass().getMethod("close");
        m.invoke(dataSource);
    }

    protected void tx(AbstractDao.Tx t) {
        DSL.using(cfg).transaction(cfg -> {
            DSLContext tx = DSL.using(cfg);
            t.run(tx);
        });
    }

    protected Configuration getConfiguration() {
        return cfg;
    }

    protected UuidGenerator getUuidGenerator() {
        return new UuidGenerator();
    }

    private static final class DatabaseConfigurationImpl implements DatabaseConfiguration {

        private final String url;
        private final String username;
        private final String password;
        private final int maxPoolSize;

        private DatabaseConfigurationImpl(String url, String username, String password, int maxPoolSize) {
            this.url = url;
            this.username = username;
            this.password = password;
            this.maxPoolSize = maxPoolSize;
        }

        @Override
        public String url() {
            return url;
        }

        @Override
        public String username() {
            return username;
        }

        @Override
        public String password() {
            return password;
        }

        @Override
        public int maxPoolSize() {
            return maxPoolSize;
        }

        @Override
        public Duration maxLifetime() {
            return Duration.ofSeconds(30);
        }

        @Override
        public Map<String, Object> changeLogParameters() {
            String fakeSecret = Base64.getEncoder().encodeToString("test".getBytes());
            return Map.of("createExtensionAvailable", "true",
                    "defaultAdminToken", fakeSecret,
                    "skipAdminTokenGeneration", "true",
                    "defaultAgentToken", fakeSecret,
                    "skipAgentTokenGeneration", "true",
                    "secretStoreSalt", fakeSecret,
                    "serverPassword", fakeSecret);
        }
    }
}
