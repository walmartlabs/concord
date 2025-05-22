package com.walmartlabs.concord.server.dist;

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

import com.walmartlabs.concord.server.ConcordServer;
import com.walmartlabs.concord.server.Version;
import org.eclipse.sisu.space.BeanScanning;
import org.eclipse.sisu.space.SpaceModule;
import org.eclipse.sisu.space.URLClassSpace;
import org.eclipse.sisu.wire.WireModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        SLF4JBridgeHandler.install();

        Version v = Version.getCurrent();
        log.info("Starting Concord Server ({}, {}, {})...", v.getVersion(), v.getCommitId(), v.getEnv());

        long t1 = System.currentTimeMillis();

        ConcordServer server = autoWire();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Received SIGTERM, stopping...");
            try {
                server.stop();
            } catch (Exception e) {
                log.warn("Failed to stop the server: {}", e.getMessage());
            }
        }, "shutdown-hook"));
        server.start();

        long t2 = System.currentTimeMillis();
        log.info("main -> started in {}ms", (t2 - t1));
    }

    public static ConcordServer autoWire() throws Exception {
        // works as a plugin system by automatically wiring all @Named modules and beans in the classpath
        ClassLoader cl = ConcordServer.class.getClassLoader();
        return ConcordServer.withModules(new WireModule(new SpaceModule(new URLClassSpace(cl), BeanScanning.GLOBAL_INDEX)));
    }
}
