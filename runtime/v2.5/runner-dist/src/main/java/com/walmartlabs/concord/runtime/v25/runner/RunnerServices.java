package com.walmartlabs.concord.runtime.v25.runner;

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

import com.walmartlabs.concord.client2.ApiClient;
import com.walmartlabs.concord.client2.ClientUtils;
import com.walmartlabs.concord.client2.CreateSecretRequest;
import com.walmartlabs.concord.client2.LockResult;
import com.walmartlabs.concord.client2.ProcessLocksApi;
import com.walmartlabs.concord.client2.SecretClient;
import com.walmartlabs.concord.client2.SecretEntryV2;
import com.walmartlabs.concord.client2.SecretOperationResponse;
import com.walmartlabs.concord.common.secret.BinaryDataSecret;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.v2.sdk.DockerContainerSpec;
import com.walmartlabs.concord.runtime.v2.sdk.DockerService;
import com.walmartlabs.concord.runtime.v2.sdk.LockService;
import com.walmartlabs.concord.runtime.v2.sdk.SecretNotFoundException;
import com.walmartlabs.concord.runtime.v2.sdk.SecretService;
import com.walmartlabs.concord.sdk.Secret;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** SDK service adapters that depend only on client2 and the v2 SDK contracts. */
final class RunnerServices {

    private static final String WORKSPACE_TARGET = "/workspace";
    private static final String INSTANCE_LABEL = "concordTxId";
    private static final Duration TERMINATION_GRACE = Duration.ofSeconds(5);

    static DockerService docker(Path workDirectory, RunnerConfiguration configuration, UUID instanceId) {
        return docker(workDirectory, configuration, instanceId, TERMINATION_GRACE,
                (command, redirectErrorStream) -> new ProcessBuilder(command)
                        .redirectErrorStream(redirectErrorStream).start());
    }

    static DockerService docker(Path workDirectory, RunnerConfiguration configuration, UUID instanceId,
                                Duration terminationGrace, ProcessStarter processStarter) {
        Objects.requireNonNull(workDirectory, "workDirectory");
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(terminationGrace, "terminationGrace");
        Objects.requireNonNull(processStarter, "processStarter");
        if (terminationGrace.isNegative() || terminationGrace.isZero()) {
            throw new IllegalArgumentException("terminationGrace must be positive");
        }

        return (spec, out, err) -> {
            var command = dockerCommand(workDirectory, configuration, instanceId, spec);
            return startDocker(processStarter.start(command, spec.redirectErrorStream()), spec, workDirectory, out, err,
                    terminationGrace);
        };
    }

    static List<String> dockerCommand(Path workDirectory, RunnerConfiguration configuration, UUID instanceId,
                                      DockerContainerSpec spec) {
        var command = new ArrayList<String>();
        command.add("docker");
        command.add("run");
        command.add("--rm");
        if (spec.name() != null) {
            command.addAll(List.of("--name", spec.name()));
        }
        if (spec.user() != null) {
            command.addAll(List.of("--user", spec.user()));
        }
        if (spec.workdir() != null) {
            command.addAll(List.of("--workdir", spec.workdir()));
        }
        if (spec.entryPoint() != null) {
            command.addAll(List.of("--entrypoint", spec.entryPoint()));
        }
        if (spec.cpu() != null) {
            command.addAll(List.of("--cpus", spec.cpu()));
        }
        if (spec.memory() != null) {
            command.addAll(List.of("--memory", spec.memory()));
        }
        command.addAll(List.of("--volume", workDirectory.toAbsolutePath() + ":" + WORKSPACE_TARGET));
        configuration.docker().extraVolumes().forEach(volume -> command.addAll(List.of("--volume", volume)));
        if (spec.envFile() != null) {
            command.addAll(List.of("--env-file", spec.envFile()));
        }
        effectiveEnv(spec.env(), configuration.docker().exposeDockerDaemon())
                .forEach((key, value) -> command.addAll(List.of("--env", key + "=" + value)));
        if (spec.labels() != null) {
            spec.labels().forEach((key, value) -> command.addAll(List.of("--label", key + "=" + value)));
        }
        command.addAll(List.of("--label", INSTANCE_LABEL + "=" + instanceId));
        if (spec.options() != null && spec.options().hosts() != null) {
            spec.options().hosts().forEach(host -> command.addAll(List.of("--add-host", host)));
        }
        command.add(spec.image());
        if (spec.args() != null) {
            command.addAll(spec.args());
        }
        return command;
    }

    private static int startDocker(Process process, DockerContainerSpec spec, Path workDirectory,
                                   DockerService.LogCallback out, DockerService.LogCallback err, Duration terminationGrace)
            throws InterruptedException, IOException {
        var failures = new AtomicReference<Throwable>();
        Thread stdout = null;
        Thread stderr = null;
        try {
            var outputFile = outputFile(workDirectory, spec.stdOutFilePath());
            stdout = start(() -> copy(process.getInputStream(), out, outputFile, failures));
            stderr = spec.redirectErrorStream() ? null : start(() -> copy(process.getErrorStream(), err, null, failures));
            var result = process.waitFor();
            join(stdout);
            if (stderr != null) {
                join(stderr);
            }
            var failure = failures.get();
            if (failure instanceof IOException e) {
                throw e;
            }
            if (failure != null) {
                throw new IllegalStateException("Cannot read container output", failure);
            }
            return result;
        } catch (InterruptedException e) {
            terminate(process, stdout, stderr, terminationGrace, e);
            Thread.currentThread().interrupt();
            throw e;
        } catch (IOException | RuntimeException | Error e) {
            terminate(process, stdout, stderr, terminationGrace, e);
            throw e;
        }
    }

    private static Map<String, String> effectiveEnv(Map<String, String> env, boolean exposeDockerDaemon) {
        var result = new LinkedHashMap<String, String>();
        if (exposeDockerDaemon) {
            result.put("DOCKER_HOST", Objects.requireNonNullElse(System.getenv("DOCKER_HOST"),
                    "unix:///var/run/docker.sock"));
        }
        if (env != null) {
            result.putAll(env);
        }
        return result;
    }

    private static void terminate(Process process, Thread stdout, Thread stderr, Duration grace, Throwable primary) {
        boolean interrupted = false;
        process.destroy();
        try {
            if (!process.waitFor(grace.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor();
            }
        } catch (InterruptedException e) {
            primary.addSuppressed(e);
            interrupted = true;
            process.destroyForcibly();
            try {
                process.waitFor();
            } catch (InterruptedException suppressed) {
                primary.addSuppressed(suppressed);
                interrupted = true;
            }
        } finally {
            close(process.getInputStream(), primary);
            close(process.getErrorStream(), primary);
            join(stdout, primary);
            join(stderr, primary);
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void close(InputStream stream, Throwable primary) {
        try {
            stream.close();
        } catch (IOException e) {
            primary.addSuppressed(e);
        }
    }

    private static void join(Thread thread) throws InterruptedException {
        thread.join();
    }

    private static void join(Thread thread, Throwable primary) {
        if (thread == null) {
            return;
        }
        boolean interrupted = false;
        while (thread.isAlive()) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                primary.addSuppressed(e);
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    static LockService locks(ApiClient client, UUID instanceId) {
        return new LockService() {
            @Override
            public void projectLock(String lockName) throws Exception {
                var api = new ProcessLocksApi(client);
                while (!Thread.currentThread().isInterrupted()) {
                    LockResult result = ClientUtils.withRetry(3, 5000,
                            () -> api.tryLock(instanceId, lockName, "PROJECT"));
                    if (result.getAcquired()) {
                        return;
                    }
                    Thread.sleep(10_000);
                }
                throw new InterruptedException("Interrupted while acquiring project lock '" + lockName + "'");
            }

            @Override
            public void projectUnlock(String lockName) throws Exception {
                ClientUtils.withRetry(3, 5000, () -> {
                    new ProcessLocksApi(client).unlock(instanceId, lockName, "PROJECT");
                    return null;
                });
            }
        };
    }

    static SecretService secrets(RunnerConfiguration configuration, ApiClient client, UUID instanceId, Path workDirectory) {
        return new Secrets(configuration, client, instanceId, workDirectory);
    }

    static com.walmartlabs.concord.runtime.v2.sdk.FileService files(Path workDirectory) {
        return new com.walmartlabs.concord.runtime.v2.sdk.FileService() {
            @Override
            public Path createTempFile(String prefix, String suffix) throws IOException {
                return Files.createTempFile(temporaryDirectory(), prefix, suffix);
            }

            @Override
            public Path createTempDirectory(String prefix) throws IOException {
                return Files.createTempDirectory(temporaryDirectory(), prefix);
            }

            private Path temporaryDirectory() throws IOException {
                var directory = workDirectory.resolve(".concord").resolve("tmp");
                Files.createDirectories(directory);
                return directory;
            }
        };
    }

    private static Path outputFile(Path workDirectory, String path) throws IOException {
        if (path == null) {
            return null;
        }
        var result = Path.of(path);
        if (!result.isAbsolute()) {
            result = workDirectory.resolve(result);
        }
        var parent = result.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        return result;
    }

    private static void copy(InputStream input, DockerService.LogCallback callback, Path outputFile,
                             AtomicReference<Throwable> failures) {
        try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
             BufferedWriter writer = outputFile == null ? null : Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
            for (String line; (line = reader.readLine()) != null;) {
                if (callback != null) {
                    callback.onLog(line);
                }
                if (writer != null) {
                    writer.write(line);
                    writer.newLine();
                }
            }
        } catch (IOException | RuntimeException e) {
            failures.compareAndSet(null, e);
        }
    }

    private static Thread start(Runnable action) {
        var thread = new Thread(action, "v25-docker-log");
        thread.start();
        return thread;
    }

    @FunctionalInterface
    interface ProcessStarter {

        Process start(List<String> command, boolean redirectErrorStream) throws IOException;
    }

    private static final class Secrets implements SecretService {
        private final SecretClient client;
        private final UUID instanceId;
        private final Path temporaryDirectory;

        private Secrets(RunnerConfiguration configuration, ApiClient apiClient, UUID instanceId, Path workDirectory) {
            this.client = new SecretClient(apiClient, configuration.api().retryCount(), configuration.api().retryInterval());
            this.instanceId = instanceId;
            this.temporaryDirectory = workDirectory.resolve(".concord").resolve("tmp");
        }

        @Override
        public SecretCreationResult createKeyPair(SecretParams secret, KeyPair keyPair) throws Exception {
            return result(client.createSecret(request(secret).keyPair(CreateSecretRequest.KeyPair.builder()
                    .publicKey(keyPair.publicKey()).privateKey(keyPair.privateKey()).build()).build()));
        }

        @Override
        public SecretCreationResult createUsernamePassword(SecretParams secret, UsernamePassword credentials) throws Exception {
            return result(client.createSecret(request(secret).usernamePassword(
                    CreateSecretRequest.UsernamePassword.of(credentials.username(), credentials.password())).build()));
        }

        @Override
        public SecretCreationResult createData(SecretParams secret, byte[] data) throws Exception {
            return result(client.createSecret(request(secret).data(data).build()));
        }

        @Override
        public String exportAsString(String orgName, String secretName, String password) throws Exception {
            BinaryDataSecret secret = get(orgName, secretName, password, SecretEntryV2.TypeEnum.DATA);
            return new String(secret.getData(), StandardCharsets.UTF_8);
        }

        @Override
        public KeyPair exportKeyAsFile(String orgName, String secretName, String password) throws Exception {
            com.walmartlabs.concord.common.secret.KeyPair keyPair = get(orgName, secretName, password,
                    SecretEntryV2.TypeEnum.KEY_PAIR);
            var directory = Files.createTempDirectory(temporaryDirectory(), "secret-");
            var privateKey = Files.write(Files.createTempFile(directory, "private", ".key"), keyPair.getPrivateKey());
            var publicKey = Files.write(Files.createTempFile(directory, "public", ".key"), keyPair.getPublicKey());
            return KeyPair.builder().privateKey(privateKey).publicKey(publicKey).build();
        }

        @Override
        public UsernamePassword exportCredentials(String orgName, String secretName, String password) throws Exception {
            com.walmartlabs.concord.common.secret.UsernamePassword credentials = get(orgName, secretName, password,
                    SecretEntryV2.TypeEnum.USERNAME_PASSWORD);
            return UsernamePassword.of(credentials.getUsername(), new String(credentials.getPassword()));
        }

        @Override
        public Path exportAsFile(String orgName, String secretName, String password) throws Exception {
            BinaryDataSecret secret = get(orgName, secretName, password, SecretEntryV2.TypeEnum.DATA);
            return Files.write(Files.createTempFile(temporaryDirectory(), "secret-", ".bin"), secret.getData());
        }

        @Override
        public String decryptString(String encryptedValue) throws Exception {
            return new String(client.decryptString(instanceId, Base64.getDecoder().decode(encryptedValue)), StandardCharsets.UTF_8);
        }

        @Override
        public String encryptString(String orgName, String projectName, String value) throws Exception {
            return client.encryptString(orgName, projectName, value);
        }

        private Path temporaryDirectory() throws IOException {
            Files.createDirectories(temporaryDirectory);
            return temporaryDirectory;
        }

        @SuppressWarnings("unchecked")
        private <T extends Secret> T get(String orgName, String secretName, String password, SecretEntryV2.TypeEnum type)
                throws Exception {
            try {
                return client.getData(orgName, secretName, password, type);
            } catch (com.walmartlabs.concord.client2.SecretNotFoundException e) {
                throw new SecretNotFoundException(e.getOrgName(), e.getSecretName());
            }
        }

        private static SecretCreationResult result(SecretOperationResponse response) {
            return SecretCreationResult.builder().id(response.getId()).password(response.getPassword()).build();
        }

        private static com.walmartlabs.concord.client2.ImmutableCreateSecretRequest.Builder request(SecretParams secret) {
            var visibility = secret.visibility();
            var result = CreateSecretRequest.builder().org(secret.orgName()).name(secret.secretName())
                    .generatePassword(secret.generatePassword()).storePassword(secret.storePassword())
                    .visibility(visibility != null ? SecretEntryV2.VisibilityEnum.fromValue(visibility.name()) : null);
            if (secret.project() != null) {
                result.addProjectNames(Objects.requireNonNull(secret.project()));
            }
            return result;
        }
    }

    private RunnerServices() {
    }
}
