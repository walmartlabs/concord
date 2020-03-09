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
import java.util.*;

public class Main {

    public static void main(String[] args) throws Exception {
        Path workDir = Paths.get(System.getProperty("user.dir"));
        RunnerConfiguration runnerCfg = readRunnerConfiguration(args);

        ClassLoader parentClassLoader = Main.class.getClassLoader();
        Injector injector = new InjectorFactory(parentClassLoader, new WorkingDirectory(workDir), runnerCfg,
                ServicesModule.builder()
                        // TODO: add SecretService implementation
                        // TODO: add DockerService implementation
                        .build())
                .create();

        // if "pre-forking" is used then the runner starts with an empty
        // working directory and must wait for the instanceId file to appear
        // which indicates that the working directory is ready and contains
        // the process' files
        UUID instanceId = waitForInstanceId(workDir);

        Runner runner = new Runner.Builder(instanceId, workDir)
                .injector(injector)
                .listener(injector.getInstance(EventRecordingExecutionListener.class))
                .build();

        ProcessConfiguration cfg = readProcessConfiguration(workDir);

        // use LinkedHashMap to preserve the order of the keys
        Map<String, Object> processArgs = new LinkedHashMap<>(cfg.arguments());
        // save the current process ID as an argument, flows and plugins expect it to be a string value
        processArgs.put(Constants.Context.TX_ID_KEY, instanceId.toString());

        ProcessSnapshot snapshot;
        Set<String> events = StateManager.readResumeEvents(workDir); // TODO make it an interface
        if (events == null || events.isEmpty()) {
            snapshot = start(runner, cfg, processArgs);
        } else {
            snapshot = resume(runner, workDir, cfg, processArgs, events);
        }

        if (isSuspended(snapshot)) {
            StateManager.finalizeSuspendedState(workDir, snapshot, getEvents(snapshot));  // TODO make it an interface
        } else {
            StateManager.cleanupState(workDir);  // TODO make it an interface
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

    /**
     * Waits until an instanceId file appears in the specified directory
     * then reads it and parses as UUID.
     */
    private static UUID waitForInstanceId(Path workDir) throws IOException {
        Path p = workDir.resolve(Constants.Files.INSTANCE_ID_FILE_NAME);
        while (true) {
            if (Files.exists(p)) {
                String s = new String(Files.readAllBytes(p));
                return UUID.fromString(s.trim());
            }

            // TODO maybe use something more sophisticated, like a file watcher
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static ProcessConfiguration readProcessConfiguration(Path workDir) throws IOException {
        Path p = workDir.resolve(Constants.Files.REQUEST_DATA_FILE_NAME);
        if (!Files.exists(p)) {
            return ProcessConfiguration.builder().build();
        }

        //TODO: singleton?
        ObjectMapper om = new ObjectMapper();
        try (InputStream in = Files.newInputStream(p)) {
            return om.readValue(in, ProcessConfiguration.class);
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

    public static boolean isSuspended(ProcessSnapshot snapshot) {
        return snapshot.vmState().threadStatus().entrySet().stream()
                .anyMatch(e -> e.getValue() == ThreadStatus.SUSPENDED);
    }

    public static Set<String> getEvents(ProcessSnapshot snapshot) {
        // TODO validate for uniqueness?
        return new HashSet<>(snapshot.vmState().getEventRefs().values());
    }
}
