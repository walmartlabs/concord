package com.walmartlabs.concord.cli;

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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.inject.Key;
import com.google.inject.name.Names;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.forms.Form;
import com.walmartlabs.concord.runtime.common.StateManager;
import com.walmartlabs.concord.runtime.v2.runner.ProcessSnapshot;
import com.walmartlabs.concord.runtime.v2.runner.Runner;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskProviders;
import com.walmartlabs.concord.runtime.v2.runner.vm.ParallelExecutionException;
import com.walmartlabs.concord.runtime.v2.sdk.UserDefinedException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

@Command(name = "resume", description = "Resume a previously suspended local runtime-v2 workspace.")
public class Resume implements Callable<Integer> {

    private static final ObjectMapper INPUT_OBJECT_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "display the command's help message")
    boolean helpRequested = false;

    @Option(names = {"-v", "--verbose"}, description = {
            "Specify multiple -v options to increase verbosity. For example, `-v -v -v` or `-vvv`",
            "-v log flow steps",
            "-vv log task input/output args",
            "-vvv runner debug logs"})
    boolean[] verbosity = new boolean[0];

    @Option(names = {"--event"}, description = "Waiting event reference to resume")
    String event;

    @Option(names = {"--input-file"}, description = "Resume input payload in JSON or YAML format")
    Path inputFile;

    @Option(names = {"-e", "--extra-vars"}, description = "inline resume input values (key=value)")
    Map<String, String> extraVars = new LinkedHashMap<>();

    @Option(names = {"--save-as"}, description = "Wrap the payload under the specified variable path")
    String saveAs;

    @Parameters(arity = "0..1", description = "Prepared workspace directory containing suspended local state (default: current directory or ./target).")
    Path workDir;

    @Override
    public Integer call() throws Exception {
        var verbosity = new Verbosity(this.verbosity);
        var workDir = resolveWorkDir();

        if (saveAs != null && inputFile == null && extraVars.isEmpty()) {
            return err("--save-as requires --input-file or -e/--extra-vars");
        }

        try {
            var metadata = loadMetadata(workDir);
            var waitingEvents = loadWaitingEvents(workDir);
            List<Form> pendingForms = LocalFormState.syncPendingForms(workDir, waitingEvents);
            String selectedEvent = null;
            if (!usesFormMode(pendingForms)) {
                selectedEvent = selectEvent(waitingEvents);
            }
            if (!usesFormMode(pendingForms) && selectedEvent == null) {
                return 1;
            }

            var dependencyManager = LocalCliRuntime.createDependencyManager(Path.of(metadata.depsCacheDir()));
            var injector = LocalCliRuntime.createInjector(workDir,
                    metadata.runnerConfiguration(),
                    metadata.processConfiguration(),
                    metadata.toCliConfigContext(),
                    Path.of(metadata.defaultTaskVars()),
                    dependencyManager,
                    verbosity);

            LocalCliRuntime.notifyProjectLoaded(workDir);

            if (metadata.processConfiguration().debug()) {
                System.out.println("Available tasks: " + injector.getInstance(TaskProviders.class).names());
            }

            var classLoader = injector.getInstance(Key.get(ClassLoader.class, Names.named("runtime")));
            var snapshot = loadSnapshot(workDir, classLoader);
            if (snapshot == null) {
                return err("Missing suspended snapshot in " + workDir);
            }

            var runner = injector.getInstance(Runner.class);

            try {
                if (usesFormMode(pendingForms)) {
                    LocalFormState.assertSupported(workDir, pendingForms);
                    snapshot = LocalFormSession.resumePendingForms(workDir, runner, snapshot, metadata);
                } else {
                    var input = loadInput();
                    snapshot = runner.resume(snapshot, Collections.singleton(selectedEvent), input);
                }
            } catch (ParallelExecutionException | UserDefinedException e) {
                return -1;
            } catch (Exception e) {
                Run.logException(verbosity, e);
                return 1;
            }

            if (LocalSuspendPersistence.isSuspended(snapshot)) {
                LocalSuspendPersistence.save(workDir, snapshot, metadata);
                var events = LocalSuspendPersistence.getEvents(snapshot);
                var nextPendingForms = LocalFormState.syncPendingForms(workDir, events);
                LocalSuspendPersistence.printResumeGuidance(metadata.resumeDirPath(), events, nextPendingForms);
                return 0;
            }

            LocalSuspendPersistence.cleanup(workDir);
            System.out.println("...done!");
            return 0;
        } catch (Exception e) {
            return err(e.getMessage());
        }
    }

    private String selectEvent(Set<String> waitingEvents) {
        if (event == null || event.isBlank()) {
            if (waitingEvents.size() == 1) {
                return waitingEvents.iterator().next();
            }

            err("Multiple waiting events. Specify --event. Available events: " + String.join(", ", waitingEvents));
            return null;
        }

        if (!waitingEvents.contains(event)) {
            err("Unknown event: " + event + ". Available events: " + String.join(", ", waitingEvents));
            return null;
        }

        return event;
    }

    private Map<String, Object> loadInput() throws Exception {
        var input = new LinkedHashMap<String, Object>();

        if (inputFile != null) {
            if (!Files.exists(inputFile)) {
                throw new IllegalArgumentException("Input file not found: " + inputFile);
            }

            var node = INPUT_OBJECT_MAPPER.readTree(inputFile.toFile());
            if (node == null || !node.isObject()) {
                throw new IllegalArgumentException("Expected a JSON or YAML object in " + inputFile);
            }

            input.putAll(INPUT_OBJECT_MAPPER.convertValue(node, MAP_TYPE));
        }

        if (!extraVars.isEmpty()) {
            input.putAll(ConfigurationUtils.deepMerge(input, loadInlineInput()));
        }

        if (input.isEmpty()) {
            return Collections.emptyMap();
        }
        if (saveAs == null || saveAs.isBlank()) {
            return input;
        }

        return wrapInput(input, saveAs);
    }

    private Map<String, Object> loadInlineInput() throws Exception {
        var input = new LinkedHashMap<String, Object>();

        for (var e : extraVars.entrySet()) {
            var key = e.getKey().trim();
            if (key.isEmpty()) {
                throw new IllegalArgumentException("Invalid inline input key");
            }

            var nested = ConfigurationUtils.toNested(key, parseInlineValue(e.getValue()));
            input.putAll(ConfigurationUtils.deepMerge(input, nested));
        }

        return input;
    }

    private static Object parseInlineValue(String value) throws Exception {
        return INPUT_OBJECT_MAPPER.readValue(value, Object.class);
    }

    private static Map<String, Object> wrapInput(Map<String, Object> input, String saveAs) {
        var segments = saveAs.split("\\.");
        var current = input;

        for (int i = segments.length - 1; i >= 0; i--) {
            var segment = segments[i].trim();
            if (segment.isEmpty()) {
                throw new IllegalArgumentException("Invalid --save-as path: " + saveAs);
            }

            var wrapper = new LinkedHashMap<String, Object>();
            wrapper.put(segment, current);
            current = wrapper;
        }

        return current;
    }

    private static int err(String message) {
        System.err.println(message);
        return 1;
    }

    private boolean usesFormMode(List<Form> pendingForms) {
        return !hasGenericManualFlags() && !pendingForms.isEmpty();
    }

    private boolean hasGenericManualFlags() {
        return event != null || inputFile != null || saveAs != null || !extraVars.isEmpty();
    }

    private Path resolveWorkDir() {
        var baseDir = workDir != null ? workDir : Paths.get(System.getProperty("user.dir"));
        var normalized = baseDir.normalize().toAbsolutePath();

        if (hasSuspendedState(normalized)) {
            return normalized;
        }

        var targetDir = CliPaths.defaultTargetDir(normalized);
        if (hasSuspendedState(targetDir)) {
            return targetDir;
        }

        return normalized;
    }

    private static boolean hasSuspendedState(Path workDir) {
        return LocalSuspendPersistence.hasMetadata(workDir) && LocalSuspendPersistence.hasSnapshot(workDir);
    }

    private static LocalSuspendPersistence.ResumeMetadata loadMetadata(Path workDir) throws Exception {
        var metadata = LocalSuspendPersistence.readMetadata(workDir);
        if (metadata == null) {
            throw new IllegalArgumentException("Missing CLI resume metadata in " + workDir);
        }
        if (!LocalSuspendPersistence.hasSnapshot(workDir)) {
            throw new IllegalArgumentException("Missing suspended snapshot in " + workDir);
        }
        return metadata;
    }

    private static Set<String> loadWaitingEvents(Path workDir) throws Exception {
        Set<String> waitingEvents;
        try {
            waitingEvents = LocalSuspendPersistence.readWaitingEvents(workDir);
        } catch (Exception e) {
            throw new IllegalArgumentException("Error while reading waiting events: " + e.getMessage(), e);
        }

        if (waitingEvents == null || waitingEvents.isEmpty()) {
            throw new IllegalArgumentException("Missing suspend marker in " + workDir);
        }
        return waitingEvents;
    }

    private static ProcessSnapshot loadSnapshot(Path workDir, ClassLoader classLoader) {
        // We only resume state written by the current local CLI path, so no backward-compat fixups are needed here.
        return StateManager.readProcessState(workDir, classLoader);
    }
}
