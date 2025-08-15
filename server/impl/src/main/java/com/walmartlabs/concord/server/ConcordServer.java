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
import com.walmartlabs.concord.server.message.MessageChannelManager;

import javax.inject.Inject;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class ConcordServer {

    @Inject
    private Injector injector;

    @Inject
    private BackgroundTasks tasks;

    @Inject
    private HttpServer server;

    @Inject
    private MessageChannelManager messageChannelManager;

    private final Lock controlMutex = new ReentrantLock();

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
        return instance;
    }

    public ConcordServer start() throws Exception {
        controlMutex.lock();
        try {
            tasks.start();
            server.start();
        } finally {
            controlMutex.unlock();
        }
        return this;
    }

    public void stop() throws Exception {
        controlMutex.lock();
        try {
            if (messageChannelManager != null) {
                messageChannelManager.shutdown();
                messageChannelManager = null;
            }

            if (server != null) {
                server.stop();
                server = null;
            }

            if (tasks != null) {
                tasks.stop();
                tasks = null;
            }
        } finally {
            controlMutex.unlock();
        }
    }

    public Injector getInjector() {
        return injector;
    }

    private ConcordServer() {
    }
}
