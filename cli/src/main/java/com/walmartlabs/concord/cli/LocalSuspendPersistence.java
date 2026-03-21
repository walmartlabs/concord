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
import com.walmartlabs.concord.runtime.common.StateManager;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.v2.runner.ProcessSnapshot;
import com.walmartlabs.concord.runtime.v2.runner.StateBackwardCompatibility;
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

    static ProcessSnapshot applyBackwardCompatibility(ProcessSnapshot snapshot) {
        return StateBackwardCompatibility.apply(snapshot);
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
        var stateDir = stateDir(workDir);
        if (Files.exists(stateDir)) {
            PathUtils.deleteRecursively(stateDir);
        }
    }

    static void printResumeGuidance(Path resumeDir, Set<String> events) {
        System.out.println("Process suspended.");

        var cmd = resumeCommand(resumeDir);

        if (events.size() == 1) {
            var event = events.iterator().next();
            System.out.println("Waiting event: " + event);
            System.out.println("Resume with: " + cmd + " --event " + event);
            return;
        }

        System.out.println("Waiting events: " + String.join(", ", events));
        System.out.println("Resume with: " + cmd + " --event <eventRef>");
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

    private static String resumeCommand(Path resumeDir) {
        var currentDir = Path.of(System.getProperty("user.dir")).normalize().toAbsolutePath();
        var normalizedResumeDir = resumeDir.normalize().toAbsolutePath();
        if (normalizedResumeDir.equals(currentDir)) {
            return "concord resume";
        }
        return "concord resume " + normalizedResumeDir;
    }

    record ResumeMetadata(ProcessConfiguration processConfiguration,
                          RunnerConfiguration runnerConfiguration,
                          List<String> activeProfiles,
                          String resumeDir,
                          String workDir,
                          String defaultTaskVars,
                          String depsCacheDir,
                          RemoteRunData remoteRun,
                          SecretsData secrets) {

        static ResumeMetadata from(Path workDir,
                                   Path resumeDir,
                                   Path defaultTaskVars,
                                   Path depsCacheDir,
                                   List<String> activeProfiles,
                                   ProcessConfiguration processConfiguration,
                                   RunnerConfiguration runnerConfiguration,
                                   CliConfigContext cliConfigContext) {

            return new ResumeMetadata(processConfiguration,
                    runnerConfiguration,
                    List.copyOf(activeProfiles),
                    resumeDir.toString(),
                    workDir.toString(),
                    defaultTaskVars.toString(),
                    depsCacheDir.toString(),
                    RemoteRunData.from(cliConfigContext.remoteRun()),
                    SecretsData.from(cliConfigContext.secrets()));
        }

        CliConfigContext toCliConfigContext() {
            return new CliConfigContext(remoteRun != null ? remoteRun.toCliConfig() : null,
                    Objects.requireNonNull(secrets, "secrets").toCliConfig());
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

    record RemoteRunData(String baseUrl, ApiKeyRefData apiKeyRef) {

        static RemoteRunData from(CliConfig.RemoteRunConfiguration remoteRunConfiguration) {
            if (remoteRunConfiguration == null) {
                return null;
            }

            return new RemoteRunData(remoteRunConfiguration.baseUrl(),
                    ApiKeyRefData.from(remoteRunConfiguration.apiKeyRef()));
        }

        CliConfig.RemoteRunConfiguration toCliConfig() {
            return new CliConfig.RemoteRunConfiguration(baseUrl, apiKeyRef != null ? apiKeyRef.toCliConfig() : null);
        }
    }

    record ApiKeyRefData(String orgName, String secretName) {

        static ApiKeyRefData from(CliConfig.SecretRef secretRef) {
            if (secretRef == null) {
                return null;
            }

            return new ApiKeyRefData(secretRef.orgName(), secretRef.secretName());
        }

        CliConfig.SecretRef toCliConfig() {
            return new CliConfig.SecretRef(orgName, secretName);
        }
    }

    record SecretsData(VaultData vault, LocalSecretsData local, RemoteSecretsData remote) {

        static SecretsData from(CliConfig.SecretsConfiguration secretsConfiguration) {
            return new SecretsData(VaultData.from(secretsConfiguration.vault()),
                    LocalSecretsData.from(secretsConfiguration.local()),
                    RemoteSecretsData.from(secretsConfiguration.remote()));
        }

        CliConfig.SecretsConfiguration toCliConfig() {
            return new CliConfig.SecretsConfiguration(vault.toCliConfig(), local.toCliConfig(), remote.toCliConfig());
        }
    }

    record VaultData(String dir, String id) {

        static VaultData from(CliConfig.SecretsConfiguration.VaultConfiguration vaultConfiguration) {
            return new VaultData(vaultConfiguration.dir().toString(), vaultConfiguration.id());
        }

        CliConfig.SecretsConfiguration.VaultConfiguration toCliConfig() {
            return new CliConfig.SecretsConfiguration.VaultConfiguration(Path.of(dir), id);
        }
    }

    record LocalSecretsData(boolean enabled, boolean writable, String dir) {

        static LocalSecretsData from(CliConfig.SecretsConfiguration.FileSecretsProviderConfiguration localSecretsConfiguration) {
            return new LocalSecretsData(localSecretsConfiguration.enabled(),
                    localSecretsConfiguration.writable(),
                    localSecretsConfiguration.dir().toString());
        }

        CliConfig.SecretsConfiguration.FileSecretsProviderConfiguration toCliConfig() {
            return new CliConfig.SecretsConfiguration.FileSecretsProviderConfiguration(enabled, writable, Path.of(dir));
        }
    }

    record RemoteSecretsData(boolean enabled,
                             boolean writable,
                             String baseUrl,
                             String apiKey,
                             boolean confirmAccess) {

        static RemoteSecretsData from(CliConfig.SecretsConfiguration.RemoteSecretsProviderConfiguration remoteSecretsConfiguration) {
            return new RemoteSecretsData(remoteSecretsConfiguration.enabled(),
                    remoteSecretsConfiguration.writable(),
                    remoteSecretsConfiguration.baseUrl(),
                    remoteSecretsConfiguration.apiKey(),
                    remoteSecretsConfiguration.confirmAccess());
        }

        CliConfig.SecretsConfiguration.RemoteSecretsProviderConfiguration toCliConfig() {
            return new CliConfig.SecretsConfiguration.RemoteSecretsProviderConfiguration(enabled, writable, baseUrl, apiKey, confirmAccess);
        }
    }

    private LocalSuspendPersistence() {
    }
}
