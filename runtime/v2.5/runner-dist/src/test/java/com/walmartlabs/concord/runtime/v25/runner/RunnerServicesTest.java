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

import com.walmartlabs.concord.runtime.common.cfg.DockerConfiguration;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.v2.sdk.ImmutableDockerContainerSpec;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunnerServicesTest {

    @Test
    void buildsDockerArgumentsFromRunnerConfigurationAndInstance() {
        var instanceId = UUID.fromString("45556dbf-0a90-43ec-8ce8-aa6c7f5959b6");
        var configuration = RunnerConfiguration.builder().docker(DockerConfiguration.builder()
                .extraVolumes(List.of("/cache:/cache:ro", "/data:/data"))
                .exposeDockerDaemon(true)
                .build()).build();
        var spec = ImmutableDockerContainerSpec.builder().image("alpine:3.22")
                .env(java.util.Map.of("NAME", "value"))
                .labels(java.util.Map.of("source", "test"))
                .build();

        var command = RunnerServices.dockerCommand(Path.of("work"), configuration, instanceId, spec);

        assertEquals(List.of("docker", "run", "--rm",
                "--volume", Path.of("work").toAbsolutePath() + ":/workspace",
                "--volume", "/cache:/cache:ro",
                "--volume", "/data:/data",
                "--env", "DOCKER_HOST=" + System.getenv().getOrDefault("DOCKER_HOST", "unix:///var/run/docker.sock"),
                "--env", "NAME=value",
                "--label", "source=test",
                "--label", "concordTxId=" + instanceId,
                "alpine:3.22"), command);
    }

    @Test
    void preservesSeparateOutputAndSuccessfulExitCode() throws Exception {
        var configuration = RunnerConfiguration.builder().docker(DockerConfiguration.builder()
                .exposeDockerDaemon(false).build()).build();
        var service = RunnerServices.docker(Path.of("work"), configuration, UUID.randomUUID(), Duration.ofSeconds(1),
                (command, redirectErrorStream) -> new CompletedProcess("stdout\n", "stderr\n", 7));
        var stdout = new ArrayList<String>();
        var stderr = new ArrayList<String>();

        var exitCode = service.start(ImmutableDockerContainerSpec.builder().image("alpine:3.22")
                .redirectErrorStream(false).build(), stdout::add, stderr::add);

        assertEquals(7, exitCode);
        assertEquals(List.of("stdout"), stdout);
        assertEquals(List.of("stderr"), stderr);
    }

    @Test
    void interruptionTerminatesChildAndLogPumps() throws Exception {
        var process = new BlockingProcess();
        var configuration = RunnerConfiguration.builder().docker(DockerConfiguration.builder()
                .exposeDockerDaemon(false).build()).build();
        var service = RunnerServices.docker(Path.of("work"), configuration, UUID.randomUUID(), Duration.ofMillis(20),
                (command, redirectErrorStream) -> process);
        var failure = new AtomicReference<Throwable>();
        var interrupted = new AtomicBoolean();
        var invocation = new Thread(() -> {
            try {
                service.start(ImmutableDockerContainerSpec.builder().image("alpine:3.22")
                        .redirectErrorStream(false).build(), ignored -> { }, ignored -> { });
            } catch (Throwable e) {
                failure.set(e);
                interrupted.set(Thread.currentThread().isInterrupted());
            }
        });
        invocation.start();

        assertTrue(process.awaitLogPumps(), "log pumps did not start");
        invocation.interrupt();
        invocation.join(TimeUnit.SECONDS.toMillis(2));

        assertFalse(invocation.isAlive(), "Docker service did not finish interruption cleanup");
        assertInstanceOf(InterruptedException.class, failure.get());
        assertTrue(interrupted.get(), "interruption status was not restored");
        assertTrue(process.destroyed.get(), "Docker child was not destroyed");
        assertTrue(process.forciblyDestroyed.get(), "Docker child was not escalated after grace");
        assertTrue(process.stdout.exited.get(), "stdout log pump remains alive");
        assertTrue(process.stderr.exited.get(), "stderr log pump remains alive");
    }

    private static final class BlockingProcess extends Process {
        private final BlockingInputStream stdout = new BlockingInputStream();
        private final BlockingInputStream stderr = new BlockingInputStream();
        private final CountDownLatch logPumpsStarted = new CountDownLatch(2);
        private final AtomicBoolean destroyed = new AtomicBoolean();
        private final AtomicBoolean forciblyDestroyed = new AtomicBoolean();
        private final Object monitor = new Object();
        private boolean alive = true;

        @Override
        public java.io.OutputStream getOutputStream() {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream getInputStream() {
            stdout.started = logPumpsStarted;
            return stdout;
        }

        @Override
        public InputStream getErrorStream() {
            stderr.started = logPumpsStarted;
            return stderr;
        }

        @Override
        public int waitFor() throws InterruptedException {
            synchronized (monitor) {
                while (alive) {
                    monitor.wait();
                }
            }
            return 137;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            synchronized (monitor) {
                if (alive) {
                    monitor.wait(unit.toMillis(timeout));
                }
                return !alive;
            }
        }

        @Override
        public int exitValue() {
            if (alive) {
                throw new IllegalThreadStateException();
            }
            return 137;
        }

        @Override
        public void destroy() {
            destroyed.set(true);
        }

        @Override
        public Process destroyForcibly() {
            forciblyDestroyed.set(true);
            synchronized (monitor) {
                alive = false;
                monitor.notifyAll();
            }
            return this;
        }

        @Override
        public boolean isAlive() {
            synchronized (monitor) {
                return alive;
            }
        }

        private boolean awaitLogPumps() throws InterruptedException {
            return logPumpsStarted.await(1, TimeUnit.SECONDS);
        }
    }

    private static final class BlockingInputStream extends InputStream {
        private volatile CountDownLatch started;
        private final AtomicBoolean exited = new AtomicBoolean();
        private boolean closed;

        @Override
        public synchronized int read() throws IOException {
            CountDownLatch latch = started;
            if (latch != null) {
                latch.countDown();
                started = null;
            }
            while (!closed) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(e);
                }
            }
            exited.set(true);
            return -1;
        }

        @Override
        public synchronized void close() {
            closed = true;
            notifyAll();
        }
    }

    private static final class CompletedProcess extends Process {
        private final InputStream stdout;
        private final InputStream stderr;
        private final int exitCode;

        private CompletedProcess(String stdout, String stderr, int exitCode) {
            this.stdout = new ByteArrayInputStream(stdout.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            this.stderr = new ByteArrayInputStream(stderr.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            this.exitCode = exitCode;
        }

        @Override
        public java.io.OutputStream getOutputStream() {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream getInputStream() {
            return stdout;
        }

        @Override
        public InputStream getErrorStream() {
            return stderr;
        }

        @Override
        public int waitFor() {
            return exitCode;
        }

        @Override
        public int exitValue() {
            return exitCode;
        }

        @Override
        public void destroy() {
        }
    }
}
