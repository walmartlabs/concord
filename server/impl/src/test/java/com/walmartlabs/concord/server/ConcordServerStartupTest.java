package com.walmartlabs.concord.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import com.google.inject.AbstractModule;
import com.walmartlabs.concord.server.boot.BackgroundTasks;
import com.walmartlabs.concord.server.boot.HttpServer;
import com.walmartlabs.concord.server.message.MessageChannelManager;
import com.walmartlabs.concord.server.process.SecurityStateMigrator;
import com.walmartlabs.concord.server.security.authorization.AuthorizationService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ConcordServerStartupTest {
    private final BackgroundTasks tasks = mock(BackgroundTasks.class);
    private final HttpServer http = mock(HttpServer.class);
    private final MessageChannelManager messages = mock(MessageChannelManager.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final SecurityStateMigrator migrator = mock(SecurityStateMigrator.class);

    private ConcordServer server() throws Exception {
        return ConcordServer.withModules(new AbstractModule() {
            @Override
            protected void configure() {
                bind(BackgroundTasks.class).toProvider(() -> tasks);
                bind(HttpServer.class).toProvider(() -> http);
                bind(MessageChannelManager.class).toProvider(() -> messages);
                bind(AuthorizationService.class).toProvider(() -> authorization);
                bind(SecurityStateMigrator.class).toProvider(() -> migrator);
            }
        });
    }

    @Test
    void migratesBeforeStartingTasksAndHttp() throws Exception {
        ConcordServer server = server();
        assertSame(server, server.start());

        InOrder order = inOrder(authorization, migrator, tasks, http);
        order.verify(authorization).reconcile();
        order.verify(migrator).migrate();
        order.verify(tasks).start();
        order.verify(http).start();
    }

    @Test
    void migrationFailurePreventsTasksAndHttpFromStarting() throws Exception {
        IllegalStateException failure = new IllegalStateException("legacy class unavailable");
        doThrow(failure).when(migrator).migrate();

        assertSame(failure, assertThrows(IllegalStateException.class, () -> server().start()));
        verify(authorization).reconcile();
        verify(migrator).migrate();
        verifyNoInteractions(tasks, http);
    }
}
