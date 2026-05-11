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

    @Option(names = {"--describe-input"}, description = "Describe the expected input shape for a pending form")
    boolean describeInput = false;

    @Option(names = {"--no-prompt"}, description = "Disable interactive prompts and print recovery commands instead")
    boolean noPrompt = false;

    @Parameters(arity = "0..1", description = "Prepared workspace directory containing suspended local state (default: current directory or ./target).")
    Path workDir;

    @Override
    public Integer call() throws Exception {
        var verbosity = new Verbosity(this.verbosity);
        var workDir = resolveWorkDir();

        if (saveAs != null && inputFile == null && extraVars.isEmpty()) {
            return err(CliExitCodes.USAGE, "--save-as requires --input-file or -e/--extra-vars");
        }
        if (describeInput && hasManualInputFlags()) {
            return err(CliExitCodes.USAGE, "--describe-input can't be combined with --input-file, -e/--extra-vars or --save-as");
        }

        try {
            var metadata = loadMetadata(workDir);
            var waitingEvents = loadWaitingEvents(workDir);
            var pendingForms = LocalFormState.syncPendingForms(workDir, waitingEvents);
            var resumeDir = metadata.resumeDirPath();
            var interactiveAvailable = canPromptInteractively();
            var formMode = usesFormMode(pendingForms);

            if (describeInput) {
                return describeInput(resumeDir, waitingEvents, pendingForms);
            }

            if (formMode && !interactiveAvailable) {
                if (pendingForms.size() == 1
                        && waitingEvents.size() == 1
                        && !LocalSuspendPrinter.supportsNonInteractiveInput(pendingForms.get(0))) {
                    LocalSuspendPrinter.printUnsupportedNonInteractiveForm(resumeDir, pendingForms.get(0), false);
                    return CliExitCodes.NON_INTERACTIVE_UNSUPPORTED;
                }

                LocalSuspendPrinter.printInputRequired(resumeDir, waitingEvents, pendingForms, false);
                return CliExitCodes.INPUT_REQUIRED;
            }

            var selectedEvent = formMode ? null : selectEvent(resumeDir, waitingEvents, pendingForms);
            if (!formMode && selectedEvent.exitCode() != CliExitCodes.SUCCESS) {
                return selectedEvent.exitCode();
            }

            var dependencyManager = LocalCliRuntime.createDependencyManager(metadata.depsCacheDirPath());
            var injector = LocalCliRuntime.createInjector(workDir,
                    metadata.runnerConfiguration(),
                    metadata.processConfiguration(),
                    metadata.loadCliConfigContext(verbosity),
                    metadata.defaultTaskVarsPath(),
                    dependencyManager,
                    verbosity);

            LocalCliRuntime.notifyProjectLoaded(workDir);

            if (metadata.processConfiguration().debug()) {
                System.out.println("Available tasks: " + injector.getInstance(TaskProviders.class).names());
            }

            var classLoader = injector.getInstance(Key.get(ClassLoader.class, Names.named("runtime")));
            var snapshot = loadSnapshot(workDir, classLoader);
            if (snapshot == null) {
                return err(CliExitCodes.ERROR, "Missing suspended snapshot in " + workDir);
            }

            var runner = injector.getInstance(Runner.class);

            try {
                if (formMode) {
                    LocalFormState.assertSupported(workDir, pendingForms);
                    snapshot = LocalFormSession.resumePendingForms(workDir, runner, snapshot, metadata);
                } else {
                    var selectedForm = findPendingForm(pendingForms, selectedEvent.event());
                    if (selectedForm != null && !hasManualInputFlags()) {
                        LocalSuspendPrinter.printInputRequired(resumeDir, waitingEvents, pendingForms, interactiveAvailable);
                        return CliExitCodes.INPUT_REQUIRED;
                    }
                    if (selectedForm != null && !LocalSuspendPrinter.supportsNonInteractiveInput(selectedForm)) {
                        LocalSuspendPrinter.printUnsupportedNonInteractiveForm(resumeDir, selectedForm, interactiveAvailable);
                        return CliExitCodes.NON_INTERACTIVE_UNSUPPORTED;
                    }

                    var input = loadInput();
                    if (selectedForm != null) {
                        try {
                            input = LocalFormInputs.convertAndValidate(selectedForm, input, true).payload(selectedForm);
                        } catch (LocalFormInputs.InputException e) {
                            printFormInputErrors(e);
                            LocalSuspendPrinter.printInputRequired(resumeDir, waitingEvents, pendingForms, interactiveAvailable);
                            return CliExitCodes.INPUT_REQUIRED;
                        }
                    }
                    snapshot = runner.resume(snapshot, Collections.singleton(selectedEvent.event()), input);
                }
            } catch (ParallelExecutionException | UserDefinedException e) {
                return CliExitCodes.PROCESS_FAILED;
            } catch (Exception e) {
                Run.logException(verbosity, e);
                return CliExitCodes.ERROR;
            }

            if (LocalSuspendPersistence.isSuspended(snapshot)) {
                LocalSuspendPersistence.save(workDir, snapshot, metadata);
                var events = LocalSuspendPersistence.getEvents(snapshot);
                var nextPendingForms = LocalFormState.syncPendingForms(workDir, events);
                LocalSuspendPersistence.printResumeGuidance(resumeDir, events, nextPendingForms, interactiveAvailable);
                return CliExitCodes.SUSPENDED;
            }

            LocalSuspendPersistence.cleanup(workDir);
            System.out.println("...done!");
            return CliExitCodes.SUCCESS;
        } catch (Exception e) {
            return err(CliExitCodes.ERROR, e.getMessage());
        }
    }

    private int describeInput(Path resumeDir, Set<String> waitingEvents, List<Form> pendingForms) throws Exception {
        if (pendingForms.isEmpty()) {
            return err(CliExitCodes.USAGE, "--describe-input requires a pending form");
        }

        if (event == null || event.isBlank()) {
            if (pendingForms.size() > 1) {
                LocalSuspendPrinter.printDescribeSelectionRequired(resumeDir, waitingEvents, pendingForms);
                return CliExitCodes.INPUT_REQUIRED;
            }

            LocalSuspendPrinter.printDescribeInput(resumeDir, pendingForms.get(0));
            return CliExitCodes.SUCCESS;
        }

        var form = findPendingForm(pendingForms, event);
        if (form == null) {
            if (waitingEvents.contains(event)) {
                return err(CliExitCodes.USAGE, "--describe-input is only available for pending forms");
            }
            return err(CliExitCodes.USAGE, "Unknown event: " + event + ". Available events: " + String.join(", ", waitingEvents));
        }

        LocalSuspendPrinter.printDescribeInput(resumeDir, form);
        return CliExitCodes.SUCCESS;
    }

    private SelectionResult selectEvent(Path resumeDir, Set<String> waitingEvents, List<Form> pendingForms) {
        if (event == null || event.isBlank()) {
            if (waitingEvents.size() == 1) {
                return SelectionResult.success(waitingEvents.iterator().next());
            }

            if (!pendingForms.isEmpty()) {
                LocalSuspendPrinter.printInputRequired(resumeDir, waitingEvents, pendingForms, canPromptInteractively());
                return SelectionResult.error(CliExitCodes.INPUT_REQUIRED);
            } else {
                LocalSuspendPrinter.printEventSelectionRequired(resumeDir, waitingEvents);
                return SelectionResult.error(CliExitCodes.INPUT_REQUIRED);
            }
        }

        if (!waitingEvents.contains(event)) {
            err(CliExitCodes.USAGE, "Unknown event: " + event + ". Available events: " + String.join(", ", waitingEvents));
            return SelectionResult.error(CliExitCodes.USAGE);
        }

        return SelectionResult.success(event);
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

    private static int err(int exitCode, String message) {
        System.err.println(message);
        return exitCode;
    }

    private static void printFormInputErrors(LocalFormInputs.InputException e) {
        for (var message : e.messages()) {
            System.err.println("Invalid form input: " + message);
        }
        System.err.println();
    }

    private boolean usesFormMode(List<Form> pendingForms) {
        return !hasGenericManualFlags() && !pendingForms.isEmpty();
    }

    private boolean hasGenericManualFlags() {
        return event != null || hasManualInputFlags();
    }

    private boolean hasManualInputFlags() {
        return inputFile != null || saveAs != null || !extraVars.isEmpty();
    }

    private boolean canPromptInteractively() {
        return !noPrompt && PromptSupport.canPromptInteractively();
    }

    private static Form findPendingForm(List<Form> pendingForms, String event) {
        if (event == null) {
            return null;
        }

        return pendingForms.stream()
                .filter(f -> event.equals(f.eventName()))
                .findFirst()
                .orElse(null);
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

    private record SelectionResult(String event, int exitCode) {

        static SelectionResult success(String event) {
            return new SelectionResult(event, CliExitCodes.SUCCESS);
        }

        static SelectionResult error(int exitCode) {
            return new SelectionResult(null, exitCode);
        }
    }

    private static ProcessSnapshot loadSnapshot(Path workDir, ClassLoader classLoader) {
        // This branch is the first producer of local CLI suspended state, so direct reads are intentional for now.
        // Revisit compatibility handling here if the on-disk local suspended-state format changes after release.
        return StateManager.readProcessState(workDir, classLoader);
    }
}
