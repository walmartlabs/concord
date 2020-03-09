package com.walmartlabs.concord.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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
import com.walmartlabs.concord.server.boot.BackgroundTasks;
import com.walmartlabs.concord.server.boot.HttpServer;
import org.eclipse.sisu.space.SpaceModule;
import org.eclipse.sisu.space.URLClassSpace;
import org.eclipse.sisu.wire.WireModule;

import javax.inject.Inject;

public final class ConcordServer {

    @Inject
    private BackgroundTasks tasks;

    @Inject
    private HttpServer server;

    public static ConcordServer start() throws Exception {
        ClassLoader cl = ConcordServer.class.getClassLoader();
        Injector injector = Guice.createInjector(new WireModule(new SpaceModule(new URLClassSpace(cl))));

        ConcordServer instance = new ConcordServer();
        injector.injectMembers(instance);

        instance.tasks.start();
        instance.server.start();

        return instance;
    }

    public synchronized void stop() throws Exception {
        if (server != null) {
            server.stop();
            server = null;
        }

        if (tasks != null) {
            tasks.stop();
            tasks = null;
        }
    }

    private ConcordServer() {
    }
}
