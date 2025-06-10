package com.walmartlabs.concord.agent;

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
import org.eclipse.sisu.space.BeanScanning;
import org.eclipse.sisu.space.SpaceModule;
import org.eclipse.sisu.space.URLClassSpace;
import org.eclipse.sisu.wire.WireModule;

public class Main {

    public static void main(String[] args) throws Exception {
        // auto-wire all modules
        var classLoader = Main.class.getClassLoader();
        var modules = new WireModule(new SpaceModule(new URLClassSpace(classLoader), BeanScanning.GLOBAL_INDEX));
        var injector = Guice.createInjector(modules);

        if (args.length == 1) {
            // one-shot mode - read ProcessResponse directly from the command line, execute the process and exit
            // the current $PWD will be used as ${workDir}
            var oneShotRunner = injector.getInstance(OneShotRunner.class);
            oneShotRunner.run(args[0]);
        } else if (args.length == 0) {
            // agent mode - connect to the server's websocket and handle ProcessResponses
            var agent = injector.getInstance(Agent.class);
            agent.start();
        } else {
            throw new IllegalArgumentException("Specify the entire ProcessResponse JSON as the first argument to run in " +
                                               "the one-shot mode or run without arguments for the default (agent) mode.");
        }
    }
}
