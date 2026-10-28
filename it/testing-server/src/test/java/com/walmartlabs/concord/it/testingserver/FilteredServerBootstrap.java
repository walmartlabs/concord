package com.walmartlabs.concord.it.testingserver;

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

import ca.ibodrov.concord.webapp.WebappPluginModule;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigResolveOptions;
import com.walmartlabs.concord.server.ConcordServer;
import com.walmartlabs.concord.server.ConcordServerModule;
import com.walmartlabs.concord.server.process.ProcessSecurityContext;
import com.walmartlabs.concord.server.process.ExecutionIdentityCodec;
import com.walmartlabs.concord.server.process.state.ProcessCheckpointManager;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.SecurityUtils;
import com.walmartlabs.concord.server.security.authorization.AuthorizationService;
import com.walmartlabs.concord.server.sdk.security.AuthenticationContext;
import com.walmartlabs.concord.server.sdk.security.SubjectPrincipal;
import com.walmartlabs.concord.server.sdk.security.IdentityService;
import com.walmartlabs.concord.server.sdk.ProcessKey;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/** Fork entry point: intentionally has no compile-time reference to the removed plugin. */
public final class FilteredServerBootstrap {
    public static void main(String[] args) throws Exception {
        try {
            Class.forName("com.walmartlabs.concord.server.plugins.pfedsso.SsoToken");
            throw new AssertionError("PFed classes remain on the fork classpath");
        } catch (ClassNotFoundException expected) {
            // The test verifies physical class absence, not just an omitted Guice module.
        }
        var defaults = ConfigFactory.load("concord-server.conf", ConfigParseOptions.defaults(),
                ConfigResolveOptions.defaults().setAllowUnresolved(true)).getConfig("concord-server");
        var config = ConfigFactory.parseFile(Path.of(args[0]).toFile()).withFallback(defaults).resolve();
        var server = ConcordServer.withModules(new ConcordServerModule(config), new WebappPluginModule()).start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { server.stop(); } catch (Exception e) { e.printStackTrace(); }
        }));
        var key = new ProcessKey(UUID.fromString(args[1]), OffsetDateTime.parse(args[2]));
        var states = server.getInjector().getInstance(ProcessStateManager.class);
        var checkpoints = server.getInjector().getInstance(ProcessCheckpointManager.class);
        if (checkpoints.restoreCheckpoint(key, UUID.fromString(args[3])) == null) {
            throw new AssertionError("Checkpoint not found");
        }
        var actor = states.get(key, ".concord/current_user", in -> {
            try { return Optional.of(ExecutionIdentityCodec.decode(in.readAllBytes())); }
            catch (Exception e) { throw new IllegalStateException(e); }
        }).orElseThrow();
        var restored = states.get(key, ".concord/restored.txt", in -> {
            try { return Optional.of(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)); }
            catch (Exception e) { throw new IllegalStateException(e); }
        }).orElseThrow();
        var restoredPrincipal = server.getInjector().getInstance(IdentityService.class).forUser(
                actor.userId(), new AuthenticationContext(actor.authenticationProviderId(), null, null));
        if (!restoredPrincipal.userId().equals(actor.userId())) {
            throw new AssertionError("Restored principal refers to another account");
        }
        if (server.getInjector().getInstance(AuthorizationService.class).prepare(actor.userId()).roles().contains(Roles.ADMIN)) {
            throw new AssertionError("Restored account retained a revoked admin grant");
        }
        server.getInjector().getInstance(ProcessSecurityContext.class).runAsCurrentUser(key, () -> {
            var current = SecurityUtils.getSubject().getPrincipals().oneByType(SubjectPrincipal.class);
            if (current == null || !current.userId().equals(actor.userId())
                    || SecurityUtils.getSubject().hasRole(Roles.ADMIN)) {
                throw new AssertionError("Run-as did not restore the actor with current grants");
            }
            return null;
        });
        if (!actor.userId().toString().equals(args[4]) || !actor.authenticationProviderId().equals("sso") || !restored.equals("checkpoint-data")) {
            throw new AssertionError("Checkpoint restored wrong state: " + actor + ", " + restored);
        }
        Files.writeString(Path.of(args[5]), "restored");
        Thread.currentThread().join();
    }
}
