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
import com.google.inject.Module;
import com.walmartlabs.concord.server.boot.BackgroundTasks;
import com.walmartlabs.concord.server.boot.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Collection;
import java.util.List;

public final class ConcordServer {

    private static final Logger log = LoggerFactory.getLogger(ConcordServer.class);

    @Inject
    private Injector injector;

    @Inject
    private BackgroundTasks tasks;

    @Inject
    private HttpServer server;

    private final Object controlMutex = new Object();

    public static ConcordServer withModules(Module... modules) throws Exception {
        return withModules(List.of(modules));
    }

    /**
     * Start ConcordServer using the provided modules.
     */
    public static ConcordServer withModules(Collection<Module> modules) throws Exception {
        Injector injector = Guice.createInjector(modules);

        ConcordServer instance = new ConcordServer();
        injector.injectMembers(instance);

        log.info("start -> starting {} task(s)", instance.tasks.count());
        return instance;
    }

    public ConcordServer start() throws Exception {
        synchronized (controlMutex) {
            tasks.start();
            server.start();
        }
        return this;
    }

    public void stop() throws Exception {
        synchronized (controlMutex) {
            if (server != null) {
                server.stop();
                server = null;
            }

            if (tasks != null) {
                tasks.stop();
                tasks = null;
            }
        }
    }

    public Injector getInjector() {
        return injector;
    }

    private ConcordServer() {
    }
}
