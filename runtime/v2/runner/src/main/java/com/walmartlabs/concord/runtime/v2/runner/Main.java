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
import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.imports.NoopImportManager;
import com.walmartlabs.concord.runtime.common.ProcessHeartbeat;
import com.walmartlabs.concord.runtime.common.StateManager;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.v2.NoopImportsNormalizer;
import com.walmartlabs.concord.runtime.v2.ProjectLoaderV2;
import com.walmartlabs.concord.runtime.v2.model.ProcessConfiguration;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v2.runner.guice.ObjectMapperProvider;
import com.walmartlabs.concord.runtime.v2.runner.logging.LoggingConfigurator;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskProviders;
import com.walmartlabs.concord.runtime.v2.sdk.WorkingDirectory;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.svm.ThreadStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private final Runner runner;
    private final RunnerConfiguration runnerCfg;
    private final ProcessConfiguration processCfg;
    private final WorkingDirectory workDir;

    private final TaskProviders taskProviders;

    @Inject
    public Main(Runner runner,
                RunnerConfiguration runnerCfg,
                ProcessConfiguration processCfg,
                WorkingDirectory workDir,
                TaskProviders taskProviders) {

        this.runner = runner;
        this.runnerCfg = runnerCfg;
        this.processCfg = processCfg;
        this.workDir = workDir;
        this.taskProviders = taskProviders;
    }

    public static void main(String[] args) throws Exception {
        RunnerConfiguration runnerCfg = readRunnerConfiguration(args);

        // create the inject with all dependencies and services available before
        // the actual process' working directory is ready. It allows us to load
        // all dependencies and have them available in "pre-fork" situations
        Injector injector = InjectorFactory.createDefault(runnerCfg);

        try {
            ProcessConfiguration processCfg = injector.getInstance(ProcessConfiguration.class);
            ApiClient apiClient = injector.getInstance(ApiClient.class);
            ProcessHeartbeat heartbeat = new ProcessHeartbeat(apiClient, processCfg.instanceId(), runnerCfg.api().maxNoHeartbeatInterval());
            heartbeat.start();

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

        ObjectMapper om = ObjectMapperProvider.getInstance();
        try (InputStream in = Files.newInputStream(src)) {
            return om.readValue(in, RunnerConfiguration.class);
        }
    }

    public void execute() throws Exception {
        validate(processCfg);

        String segmentedLogDir = runnerCfg.logging().segmentedLogDir();
        if (segmentedLogDir != null) {
            LoggingConfigurator.configure(assertNotNull(processCfg.instanceId()), segmentedLogDir);
        }

        if (processCfg.debug()) {
            log.info("Available tasks: {}", taskProviders.names());
        }

        Path workDir = this.workDir.getValue();
        Map<String, Object> processArgs = prepareProcessArgs(processCfg);

        // three modes:
        //  - regular start "from scratch"
        //  - continuing from a checkpoint
        //  - resuming after suspend

        ProcessSnapshot snapshot = StateManager.readProcessState(workDir);
        Set<String> events = StateManager.readResumeEvents(workDir); // TODO make it an interface?

        switch (currentMode(snapshot, events)) {
            case START: {
                snapshot = start(runner, workDir, processCfg, processArgs);
                break;
            }
            case RESUME: {
                snapshot = resume(runner, processCfg, snapshot, processArgs, events);
                break;
            }
            case RESTART_FROM_A_CHECKPOINT: {
                snapshot = restart(runner, snapshot);
                break;
            }
        }

        if (isSuspended(snapshot)) {
            StateManager.finalizeSuspendedState(workDir, snapshot, getEvents(snapshot)); // TODO make it an interface?
        } else {
            StateManager.cleanupState(workDir); // TODO make it an interface
        }
    }

    private static void validate(ProcessConfiguration cfg) {
        if (cfg.instanceId() == null) {
            throw new IllegalStateException("ProcessConfiguration -> instanceId cannot be null");
        }
    }

    private Map<String, Object> prepareProcessArgs(ProcessConfiguration cfg) {
        // use LinkedHashMap to preserve the order of the keys
        Map<String, Object> m = new LinkedHashMap<>(cfg.arguments());

        // save the current process ID as an argument, flows and plugins expect it to be a string value
        m.put(Constants.Context.TX_ID_KEY, assertNotNull(cfg.instanceId()).toString());

        m.put(Constants.Context.WORK_DIR_KEY, workDir.getValue().toAbsolutePath().toString());

        // save processInfo and projectInfo variables
        ObjectMapper om = ObjectMapperProvider.getInstance();
        m.put(Constants.Request.PROCESS_INFO_KEY, om.convertValue(cfg.processInfo(), Map.class));
        m.put(Constants.Request.PROJECT_INFO_KEY, om.convertValue(cfg.projectInfo(), Map.class));

        return m;
    }

    private static ProcessSnapshot start(Runner runner, Path workDir, ProcessConfiguration cfg, Map<String, Object> args) throws Exception {
        // assume all imports were processed by the agent
        ProjectLoaderV2 loader = new ProjectLoaderV2(new NoopImportManager());
        ProcessDefinition processDefinition = loader.load(workDir, new NoopImportsNormalizer()).getProjectDefinition();

        Map<String, Object> initiator = cfg.initiator();
        if (initiator != null) {
            // when the process starts the process' initiator and the current user are the same
            args.put(Constants.Request.INITIATOR_KEY, initiator);
            args.put(Constants.Request.CURRENT_USER_KEY, initiator);
        }

        return runner.start(processDefinition, cfg.entryPoint(), args);
    }

    private static ProcessSnapshot restart(Runner runner, ProcessSnapshot snapshot) throws Exception {
        return runner.resume(snapshot);
    }

    private static ProcessSnapshot resume(Runner runner, ProcessConfiguration cfg,
                                          ProcessSnapshot snapshot, Map<String, Object> args,
                                          Set<String> events) throws Exception {

        Map<String, Object> initiator = cfg.initiator();
        if (initiator != null) {
            args.put(Constants.Request.INITIATOR_KEY, initiator);
        }

        Map<String, Object> currentUser = cfg.currentUser();
        if (currentUser != null) {
            args.put(Constants.Request.CURRENT_USER_KEY, currentUser);
        }

        for (String event : events) {
            snapshot = runner.resume(snapshot, event, args);
        }

        return snapshot;
    }

    private static boolean isSuspended(ProcessSnapshot snapshot) {
        return snapshot.vmState().threadStatus().entrySet().stream()
                .anyMatch(e -> e.getValue() == ThreadStatus.SUSPENDED);
    }

    private static Set<String> getEvents(ProcessSnapshot snapshot) {
        Collection<String> eventRefs = snapshot.vmState().getEventRefs().values();

        Set<String> events = new HashSet<>(eventRefs);
        if (events.size() != eventRefs.size()) {
            throw new IllegalStateException("Non-unique event refs: " + eventRefs + ". This is most likely a bug.");
        }

        return events;
    }

    private static <T> T assertNotNull(T object) {
        assert object != null;
        return object;
    }

    private static Mode currentMode(ProcessSnapshot snapshot, Set<String> events) {
        if (events == null || events.isEmpty()) {
            return snapshot != null ? Mode.RESTART_FROM_A_CHECKPOINT : Mode.START;
        }
        return Mode.RESUME;
    }

    private enum Mode {
        START,
        RESUME,
        RESTART_FROM_A_CHECKPOINT
    }
}
