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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.cli.CliConfig.CliConfigContext;
import com.walmartlabs.concord.common.PathUtils;
import com.walmartlabs.concord.common.TemporaryPath;
import com.walmartlabs.concord.forms.Form;
import com.walmartlabs.concord.runtime.common.StateManager;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.v2.runner.ProcessSnapshot;
import com.walmartlabs.concord.runtime.v2.runner.guice.ObjectMapperProvider;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.svm.ThreadStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

final class LocalSuspendPersistence {

    private static final String METADATA_FILE_NAME = "_cliResume.json";

    static boolean isSuspended(ProcessSnapshot snapshot) {
        return snapshot.vmState().threadStatus().entrySet().stream()
                .anyMatch(e -> e.getValue() == ThreadStatus.SUSPENDED);
    }

    static void save(Path workDir, ProcessSnapshot snapshot, ResumeMetadata metadata) throws IOException {
        var events = getEvents(snapshot);
        StateManager.finalizeSuspendedState(workDir, snapshot, events);
        writeMetadata(workDir, metadata);
    }

    static ResumeMetadata readMetadata(Path workDir) throws IOException {
        var metadataPath = metadataPath(workDir);
        if (Files.notExists(metadataPath)) {
            return null;
        }

        try {
            return objectMapper().readValue(metadataPath.toFile(), ResumeMetadata.class);
        } catch (IOException e) {
            throw new IOException("Error while reading CLI resume metadata: " + e.getMessage(), e);
        }
    }

    static Set<String> readWaitingEvents(Path workDir) throws IOException {
        var suspendMarker = suspendMarkerPath(workDir);
        if (Files.notExists(suspendMarker)) {
            return null;
        }

        return new LinkedHashSet<>(Files.readAllLines(suspendMarker));
    }

    static boolean hasSnapshot(Path workDir) {
        return Files.exists(snapshotPath(workDir));
    }

    static boolean hasMetadata(Path workDir) {
        return Files.exists(metadataPath(workDir));
    }

    static void cleanup(Path workDir) throws IOException {
        StateManager.cleanupState(workDir);
    }

    static void printResumeGuidance(Path resumeDir,
                                    Set<String> events,
                                    Collection<Form> pendingForms,
                                    boolean interactiveAvailable) {
        LocalSuspendPrinter.printSuspendGuidance(resumeDir, events, pendingForms, interactiveAvailable);
    }

    static Set<String> getEvents(ProcessSnapshot snapshot) {
        var eventRefs = snapshot.vmState().getEventRefs().values();

        var events = new TreeSet<>(eventRefs);
        if (events.size() != eventRefs.size()) {
            throw new IllegalStateException("Non-unique event refs: " + eventRefs + ". This is most likely a bug.");
        }

        return events;
    }

    private static void writeMetadata(Path workDir, ResumeMetadata metadata) throws IOException {
        var metadataPath = metadataPath(workDir);
        var stateDir = metadataPath.getParent();
        if (Files.notExists(stateDir)) {
            Files.createDirectories(stateDir);
        }

        try (TemporaryPath tmp = PathUtils.tempFile("cli-resume", ".json")) {
            objectMapper().writerWithDefaultPrettyPrinter().writeValue(tmp.path().toFile(), metadata);
            Files.move(tmp.path(), metadataPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapperProvider().get();
    }

    private static Path stateDir(Path workDir) {
        return workDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(Constants.Files.JOB_STATE_DIR_NAME);
    }

    private static Path metadataPath(Path workDir) {
        return stateDir(workDir).resolve(METADATA_FILE_NAME);
    }

    private static Path suspendMarkerPath(Path workDir) {
        return stateDir(workDir).resolve(Constants.Files.SUSPEND_MARKER_FILE_NAME);
    }

    private static Path snapshotPath(Path workDir) {
        return stateDir(workDir).resolve("instance");
    }

    record ResumeMetadata(ProcessConfiguration processConfiguration,
                          RunnerConfiguration runnerConfiguration,
                          List<String> activeProfiles,
                          String resumeDir,
                          String workDir,
                          String defaultTaskVars,
                          String depsCacheDir,
                          CliConfigData cliConfig) {

        static ResumeMetadata from(Path workDir,
                                   Path resumeDir,
                                   Path defaultTaskVars,
                                   Path depsCacheDir,
                                   String contextName,
                                   CliConfig.Overrides cliConfigOverrides,
                                   List<String> activeProfiles,
                                   ProcessConfiguration processConfiguration,
                                   RunnerConfiguration runnerConfiguration) {

            return new ResumeMetadata(processConfiguration,
                    runnerConfiguration,
                    List.copyOf(activeProfiles),
                    pathToString(resumeDir),
                    pathToString(workDir),
                    pathToString(defaultTaskVars),
                    pathToString(depsCacheDir),
                    CliConfigData.from(contextName, cliConfigOverrides));
        }

        CliConfigContext loadCliConfigContext(Verbosity verbosity) throws Exception {
            return Objects.requireNonNull(cliConfig, "cliConfig").load(verbosity, resumeDirPath());
        }

        Path defaultTaskVarsPath() {
            return stringToPath(defaultTaskVars, resumeDirPath());
        }

        Path depsCacheDirPath() {
            return stringToPath(depsCacheDir, resumeDirPath());
        }

        Path resumeDirPath() {
            if (resumeDir != null) {
                return Path.of(resumeDir);
            }
            var path = Path.of(workDir);
            var parent = path.getParent();
            if (parent != null && CliPaths.DEFAULT_TARGET_DIR_NAME.equals(path.getFileName().toString())) {
                return parent;
            }
            return path;
        }
    }

    record CliConfigData(String contextName,
                         boolean requiresUserConfig,
                         String secretStoreDir,
                         String vaultDir,
                         String vaultId) {

        static CliConfigData from(String contextName, CliConfig.Overrides overrides) {
            return new CliConfigData(contextName,
                    CliConfig.hasUserConfig(),
                    pathToString(overrides.secretStoreDir()),
                    pathToString(overrides.vaultDir()),
                    overrides.vaultId());
        }

        CliConfigContext load(Verbosity verbosity, Path fallbackBaseDir) throws Exception {
            try {
                if (requiresUserConfig && !CliConfig.hasUserConfig()) {
                    throw new IllegalArgumentException("CLI configuration file is missing from ~/.concord. Resume requires the stored '" + contextName + "' context.");
                }
                return CliConfig.loadOrThrow(verbosity, contextName, toOverrides(fallbackBaseDir));
            } catch (Exception e) {
                throw new IllegalArgumentException("Unable to reload CLI configuration context '" + contextName + "' for resume: " + e.getMessage(), e);
            }
        }

        private CliConfig.Overrides toOverrides(Path fallbackBaseDir) {
            return new CliConfig.Overrides(stringToPath(secretStoreDir, fallbackBaseDir), stringToPath(vaultDir, fallbackBaseDir), vaultId);
        }
    }

    private static String pathToString(Path path) {
        return path != null ? path.normalize().toAbsolutePath().toString() : null;
    }

    private static Path stringToPath(String value, Path fallbackBaseDir) {
        if (value == null) {
            return null;
        }

        var path = Path.of(value);
        if (path.isAbsolute()) {
            return path.normalize();
        }

        return fallbackBaseDir.resolve(path).normalize().toAbsolutePath();
    }

    private LocalSuspendPersistence() {
    }
}
