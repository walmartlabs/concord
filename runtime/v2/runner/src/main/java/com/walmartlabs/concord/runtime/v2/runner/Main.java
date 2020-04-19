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
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.walmartlabs.concord.runtime.common.StateManager;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.v2.sdk.WorkingDirectory;
import com.walmartlabs.concord.runtime.v2.model.ProcessConfiguration;
import com.walmartlabs.concord.runtime.v2.runner.logging.LoggingConfigurator;
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

    private final Injector injector;
    private final RunnerConfiguration runnerCfg;
    private final ProcessConfiguration processCfg;
    private final WorkingDirectory workDir;

    @Inject
    public Main(Injector injector,
                RunnerConfiguration runnerCfg,
                ProcessConfiguration processCfg,
                WorkingDirectory workDir) {

        this.injector = injector;
        this.runnerCfg = runnerCfg;
        this.processCfg = processCfg;
        this.workDir = workDir;
    }

    public static void main(String[] args) throws Exception {
        RunnerConfiguration runnerCfg = readRunnerConfiguration(args);
        
        // create the inject with all dependencies and services available before
        // the actual process' working directory is ready to go
        // it allows us to load all dependencies and have them available
        // in "pre-fork" situations
        Injector injector = InjectorFactory.createDefault(runnerCfg);

        try {
            Main main = injector.getInstance(Main.class);
            main.execute();

            System.exit(0);
        } catch (Throwable t) {
            t.printStackTrace(System.err);
            System.exit(1);
        }
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

    public void execute() throws Exception {
        validate(processCfg);

        String segmentedLogDir = runnerCfg.logging().segmentedLogDir();
        if (segmentedLogDir != null) {
            LoggingConfigurator.configure(segmentedLogDir);
        }

        Runner runner = new Runner.Builder()
                .injector(injector)
                .build();

        Map<String, Object> processArgs = prepareProcessArgs(processCfg);

        ProcessSnapshot snapshot;
        Set<String> events = StateManager.readResumeEvents(workDir.getValue()); // TODO make it an interface
        if (events == null || events.isEmpty()) {
            snapshot = start(runner, processCfg, processArgs);
        } else {
            snapshot = resume(runner, workDir.getValue(), processCfg, processArgs, events);
        }

        if (isSuspended(snapshot)) {
            StateManager.finalizeSuspendedState(workDir.getValue(), snapshot, getEvents(snapshot)); // TODO make it an interface
        } else {
            StateManager.cleanupState(workDir.getValue()); // TODO make it an interface
        }
    }

    private static void validate(ProcessConfiguration cfg) {
        if (cfg.instanceId() == null) {
            throw new IllegalStateException("ProcessConfiguration -> instanceId cannot be null");
        }
    }

    private static Map<String, Object> prepareProcessArgs(ProcessConfiguration cfg) {
        // use LinkedHashMap to preserve the order of the keys
        Map<String, Object> m = new LinkedHashMap<>(cfg.arguments());

        // save the current process ID as an argument, flows and plugins expect it to be a string value
        m.put(Constants.Context.TX_ID_KEY, cfg.instanceId().toString());

        // save processInfo and projectInfo variables
        ObjectMapper objectMapper = new ObjectMapper();
        m.put(Constants.Request.PROCESS_INFO_KEY, objectMapper.convertValue(cfg.processInfo(), Map.class));
        m.put(Constants.Request.PROJECT_INFO_KEY, objectMapper.convertValue(cfg.projectInfo(), Map.class));

        return m;
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

    private static boolean isSuspended(ProcessSnapshot snapshot) {
        return snapshot.vmState().threadStatus().entrySet().stream()
                .anyMatch(e -> e.getValue() == ThreadStatus.SUSPENDED);
    }

    private static Set<String> getEvents(ProcessSnapshot snapshot) {
        // TODO validate for uniqueness?
        return new HashSet<>(snapshot.vmState().getEventRefs().values());
    }
}
