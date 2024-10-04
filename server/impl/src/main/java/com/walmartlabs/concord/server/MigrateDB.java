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

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.typesafe.config.Config;
import com.walmartlabs.concord.config.ConfigModule;
import com.walmartlabs.concord.db.DatabaseModule;
import com.walmartlabs.concord.db.MainDB;
import org.eclipse.sisu.space.BeanScanning;
import org.eclipse.sisu.space.SpaceModule;
import org.eclipse.sisu.space.URLClassSpace;
import org.eclipse.sisu.wire.WireModule;

import javax.inject.Inject;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

public class MigrateDB {

    @Inject
    @MainDB
    private DataSource dataSource;

    public static void main(String[] args) throws Exception {
        Config cfg = ConfigModule.load("concord-server");

        Injector injector = Guice.createInjector(
                new WireModule(
                        new SpaceModule(new URLClassSpace(MigrateDB.class.getClassLoader()), BeanScanning.CACHE),
                        new ConfigModule("com.walmartlabs.concord.server", cfg),
                        new DatabaseModule()));

        new MigrateDB().run(injector);
    }

    public void run(Injector injector) throws Exception {
        injector.injectMembers(this);

        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("select 1");
        }
    }
}
