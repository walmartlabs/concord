package com.walmartlabs.concord.runtime.v2.runner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Injector;
import com.walmartlabs.concord.runtime.common.StateManager;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.v2.model.ProcessConfiguration;
import com.walmartlabs.concord.runtime.v2.runner.remote.EventRecordingExecutionListener;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.svm.ThreadStatus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class Main {

    public static void main(String[] args) throws Exception {
        RunnerConfiguration runnerCfg = readRunnerConfiguration(args);

        // create the inject with all dependencies and services available before
        // the actual process' working directory is ready to go
        // it allows us to load all dependencies and have them available
        // in "pre-fork" situations
        ClassLoader parentClassLoader = Main.class.getClassLoader();
        Injector injector = InjectorFactory.createDefault(parentClassLoader, runnerCfg);

        ProcessConfiguration cfg = injector.getInstance(ProcessConfiguration.class);
        validate(cfg);

        // TODO replace with injections
        WorkingDirectory workDir = injector.getInstance(WorkingDirectory.class);

        // use LinkedHashMap to preserve the order of the keys
        Map<String, Object> processArgs = new LinkedHashMap<>(cfg.arguments());
        // save the current process ID as an argument, flows and plugins expect it to be a string value
        processArgs.put(Constants.Context.TX_ID_KEY, cfg.instanceId().toString());

        Runner runner = new Runner.Builder()
                .injector(injector)
                .listener(injector.getInstance(EventRecordingExecutionListener.class))
                .build();

        ProcessSnapshot snapshot;
        Set<String> events = StateManager.readResumeEvents(workDir.getValue()); // TODO make it an interface
        if (events == null || events.isEmpty()) {
            snapshot = start(runner, cfg, processArgs);
        } else {
            snapshot = resume(runner, workDir.getValue(), cfg, processArgs, events);
        }

        if (isSuspended(snapshot)) {
            StateManager.finalizeSuspendedState(workDir.getValue(), snapshot, getEvents(snapshot)); // TODO make it an interface
        } else {
            StateManager.cleanupState(workDir.getValue()); // TODO make it an interface
        }
    }

    private static ProcessSnapshot start(Runner runner, ProcessConfiguration cfg, Map<String, Object> args) throws Exception {
        Map<String, Object> initiator = cfg.initiator();
        if (initiator != null) {
            // when the process starts the process' initiator and the current user are the same
            args.put(Constants.Request.INITIATOR_KEY, initiator);
            args.put(Constants.Request.CURRENT_USER_KEY, initiator);
        }

        return runner.start(cfg.entryPoint(), args);
    }

    private static ProcessSnapshot resume(Runner runner, Path workDir, ProcessConfiguration cfg, Map<String, Object> args, Set<String> events) throws Exception {
        Map<String, Object> initiator = cfg.initiator();
        if (initiator != null) {
            args.put(Constants.Request.INITIATOR_KEY, initiator);
        }

        Map<String, Object> currentUser = cfg.currentUser();
        if (currentUser != null) {
            args.put(Constants.Request.CURRENT_USER_KEY, currentUser);
        }

        ProcessSnapshot state = StateManager.readState(workDir, ProcessSnapshot.class); // TODO make it an interface
        for (String event : events) {
            state = runner.resume(state, event, args);
        }
        return state;
    }

    private static RunnerConfiguration readRunnerConfiguration(String[] args) throws IOException {
        Path src;
        if (args.length > 0) {
            src = Paths.get(args[0]);
        } else {
            throw new IllegalArgumentException("Path to the runner configuration file is required");
        }

        //TODO: singleton?
        ObjectMapper om = new ObjectMapper();
        try (InputStream in = Files.newInputStream(src)) {
            return om.readValue(in, RunnerConfiguration.class);
        }
    }

    private static boolean isSuspended(ProcessSnapshot snapshot) {
        return snapshot.vmState().threadStatus().entrySet().stream()
                .anyMatch(e -> e.getValue() == ThreadStatus.SUSPENDED);
    }

    private static Set<String> getEvents(ProcessSnapshot snapshot) {
        // TODO validate for uniqueness?
        return new HashSet<>(snapshot.vmState().getEventRefs().values());
    }

    private static void validate(ProcessConfiguration cfg) {
        if (cfg.instanceId() == null) {
            throw new IllegalStateException("ProcessConfiguration -> instanceId cannot be null");
        }
    }
}
