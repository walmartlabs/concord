package com.walmartlabs.concord.runtime.v2.runner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import com.walmartlabs.concord.client2.ApiClient;
import com.walmartlabs.concord.imports.ImportsListener;
import com.walmartlabs.concord.imports.NoopImportManager;
import com.walmartlabs.concord.runtime.common.ProcessHeartbeat;
import com.walmartlabs.concord.runtime.common.StateManager;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.v2.NoopImportsNormalizer;
import com.walmartlabs.concord.runtime.v2.ProjectLoadListener;
import com.walmartlabs.concord.runtime.v2.ProjectLoaderV2;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v2.runner.guice.ObjectMapperProvider;
import com.walmartlabs.concord.runtime.v2.runner.logging.LoggingConfigurator;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskProviders;
import com.walmartlabs.concord.runtime.v2.runner.vm.LoggedException;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import com.walmartlabs.concord.runtime.v2.sdk.UserDefinedException;
import com.walmartlabs.concord.runtime.v2.sdk.WorkingDirectory;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.svm.Frame;
import com.walmartlabs.concord.svm.ParallelExecutionException;
import com.walmartlabs.concord.svm.State;
import com.walmartlabs.concord.svm.ThreadStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private final Runner runner;
    private final RunnerConfiguration runnerCfg;
    private final ProcessConfiguration processCfg;
    private final WorkingDirectory workDir;
    private final TaskProviders taskProviders;
    private final ClassLoader classLoader;
    private final ProjectLoadListeners projectLoadListeners;

    @Inject
    public Main(Runner runner,
                RunnerConfiguration runnerCfg,
                ProcessConfiguration processCfg,
                WorkingDirectory workDir,
                TaskProviders taskProviders,
                @Named("runtime") ClassLoader classLoader,
                ProjectLoadListeners projectLoadListeners) {

        this.runner = runner;
        this.runnerCfg = runnerCfg;
        this.processCfg = processCfg;
        this.workDir = workDir;
        this.taskProviders = taskProviders;
        this.classLoader = classLoader;
        this.projectLoadListeners = projectLoadListeners;
    }

    public static void main(String[] args) throws Exception {
        RunnerConfiguration runnerCfg = readRunnerConfiguration(args);

        // create the injector with all dependencies and services available before
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
        } catch (LoggedException e) {
            System.exit(1);
        } catch (UserDefinedException e) {
            log.error(e.getMessage());
            System.exit(1);
        } catch (ParallelExecutionException e) {
            log.error("{}", e.getMessage());
            System.exit(1);
        } catch (Throwable t) {
            log.error("", t);
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

        LoggingConfigurator.configure(runnerCfg.logging().segmentedLogs());

        if (processCfg.debug()) {
            log.info("Available tasks: {}", taskProviders.names().stream().sorted().collect(Collectors.toList()));
        }

        Path workDir = this.workDir.getValue();

        // three modes:
        //  - regular start "from scratch" (or running a "handler" process)
        //  - continuing from a checkpoint
        //  - resuming after suspend

        ProcessSnapshot snapshot = StateManager.readProcessState(workDir, classLoader);
        Set<String> events = StateManager.readResumeEvents(workDir); // TODO make it an interface?

        Action action = currentAction(events);
        switch (action) {
            case START: {
                Map<String, Object> processArgs = new LinkedHashMap<>();
                if (snapshot != null) {
                    // grab top-level variables from the snapshot and use them as process arguments
                    processArgs.putAll(getTopLevelVariables(snapshot));
                }
                processArgs.putAll(prepareProcessArgs(processCfg));

                snapshot = start(runner, processCfg, workDir, processArgs, projectLoadListeners);
                break;
            }
            case RESUME: {
                Map<String, Object> processArgs = prepareProcessArgs(processCfg);

                snapshot = resume(runner, processCfg, snapshot, processArgs, events);
                break;
            }
            default: {
                throw new IllegalStateException("Unsupported action: " + action);
            }
        }

        if (isSuspended(snapshot)) {
            StateManager.finalizeSuspendedState(workDir, snapshot, getEvents(snapshot)); // TODO make it an interface?
        } else {
            StateManager.cleanupState(workDir); // TODO make it an interface
        }
    }

    private Map<String, Object> prepareProcessArgs(ProcessConfiguration cfg) {
        // use LinkedHashMap to preserve the order of the keys
        Map<String, Object> m = new LinkedHashMap<>(cfg.arguments());

        // save the current process ID as an argument, flows and plugins expect it to be a string value
        m.put(Constants.Context.TX_ID_KEY, Objects.requireNonNull(cfg.instanceId()).toString());

        m.put(Constants.Context.WORK_DIR_KEY, workDir.getValue().toAbsolutePath().toString());

        // save processInfo and projectInfo variables
        ObjectMapper om = ObjectMapperProvider.getInstance();
        m.put(Constants.Request.PROCESS_INFO_KEY, om.convertValue(cfg.processInfo(), Map.class));
        m.put(Constants.Request.PROJECT_INFO_KEY, om.convertValue(cfg.projectInfo(), Map.class));

        return m;
    }

    private static Map<String, Serializable> getTopLevelVariables(ProcessSnapshot snapshot) {
        State state = snapshot.vmState();
        List<Frame> frames = state.getFrames(state.getRootThreadId());
        Frame rootFrame = frames.get(frames.size() - 1);
        return rootFrame.getLocals();
    }

    private static void validate(ProcessConfiguration cfg) {
        if (cfg.instanceId() == null) {
            throw new IllegalStateException("ProcessConfiguration -> instanceId cannot be null");
        }
    }

    private static ProcessSnapshot start(Runner runner, ProcessConfiguration cfg, Path workDir, Map<String, Object> args, ProjectLoadListener projectLoadListener) throws Exception {
        // assume all imports were processed by the agent
        ProjectLoaderV2 loader = new ProjectLoaderV2(new NoopImportManager());
        ProcessDefinition processDefinition = loader.load(workDir, new NoopImportsNormalizer(), ImportsListener.NOP_LISTENER, projectLoadListener).getProjectDefinition();

        Map<String, Object> initiator = cfg.initiator();
        if (initiator != null) {
            // when the process starts the process' initiator and the current user are the same
            args.put(Constants.Request.INITIATOR_KEY, initiator);
            args.put(Constants.Request.CURRENT_USER_KEY, initiator);
        }

        return runner.start(cfg, processDefinition, args);
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

        return runner.resume(snapshot, events, args);
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

    private static Action currentAction(Set<String> events) {
        if (events != null && !events.isEmpty()) {
            return Action.RESUME;
        }

        return Action.START;
    }

    private enum Action {
        /**
         * Regular start. If there's a process snapshot (e.g. a handler process like "onCancel"),
         * its variables from the root frame are passed as process arguments.
         */
        START,

        /**
         * Resuming with an event (e.g. a form event).
         * Previous state + an event ref.
         */
        RESUME,
    }
}
