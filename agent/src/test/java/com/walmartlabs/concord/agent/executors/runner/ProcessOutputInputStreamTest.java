package com.walmartlabs.concord.agent.executors.runner;

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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Timeout(20)
class ProcessOutputInputStreamTest {

    @TempDir
    Path dir;

    @Test
    void realJvmExitEndsReadingWhileDescendantStillHoldsPipe() throws Exception {
        try (var execution = start("held")) {
            assertTrue(execution.process.waitFor(10, TimeUnit.SECONDS));
            var child = child();
            assertTrue(child.isAlive(), "the descendant must still hold stdout open");
            var output = execution.output.get(2, TimeUnit.SECONDS);
            assertArrayEquals("runner-tail\n".getBytes(java.nio.charset.StandardCharsets.UTF_8), output);
            assertTrue(child.isAlive(), "reading must finish without killing the descendant");
        }
    }

    @Test
    void preservesLargeMergedStdoutAndStderrIncludingFinalBytes() throws Exception {
        try (var execution = start("large")) {
            var output = execution.output.get(15, TimeUnit.SECONDS);
            var block = new byte[8192];
            for (var i = 0; i < block.length; i++) {
                block[i] = (byte) (i % 251);
            }
            var expected = new ByteArrayOutputStream();
            for (var i = 0; i < 2048; i++) {
                expected.writeBytes(block);
            }
            expected.writeBytes(new byte[]{0, -1, 17});
            assertArrayEquals(expected.toByteArray(), output);
            assertEquals(0, execution.process.waitFor());
        }
    }

    @Test
    void continuouslyWritingDescendantDoesNotExtendReadingAfterRunnerExit() throws Exception {
        try (var execution = start("busy")) {
            assertTrue(execution.process.waitFor(10, TimeUnit.SECONDS));
            var output = execution.output.get(2, TimeUnit.SECONDS);
            assertTrue(output.length > 0);
            assertTrue(child().isAlive());
        }
    }

    @Test
    void interruptionStopsIdleReadingWithoutWaitingForProcessExit() throws Exception {
        var process = launch("holder");
        var result = new CompletableFuture<Boolean>();
        var reader = Thread.ofPlatform().start(() -> {
            try (var input = new ProcessOutputInputStream(process)) {
                input.transferTo(OutputStream.nullOutputStream());
                result.complete(false);
            } catch (InterruptedIOException e) {
                result.complete(Thread.currentThread().isInterrupted());
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
        });
        try {
            while (!Files.exists(dir.resolve("ready"))) {
                assertTrue(process.isAlive());
                Thread.sleep(10);
            }
            reader.interrupt();
            assertTrue(result.get(2, TimeUnit.SECONDS));
            assertTrue(process.isAlive());
        } finally {
            process.destroyForcibly();
            reader.join(Duration.ofSeconds(2));
        }
    }

    @Test
    void exitBoundaryIncludesBufferedTailButDoesNotFollowLaterWrites() throws Exception {
        var process = mock(Process.class);
        var availableCalls = new java.util.concurrent.atomic.AtomicInteger();
        var closed = new java.util.concurrent.atomic.AtomicBoolean();
        var source = new ByteArrayInputStream(new byte[]{1, 2, 3, 4}) {
            @Override
            public int available() {
                return availableCalls.incrementAndGet() == 1 ? 2 : 4;
            }

            @Override
            public void close() {
                closed.set(true);
            }
        };
        when(process.getInputStream()).thenReturn(source);
        // Only two bytes are present when exit is observed; later bytes must not
        // move the boundary. Small reads also exercise draining over several calls.
        try (var input = new ProcessOutputInputStream(process)) {
            assertEquals(1, input.read());
            assertEquals(2, input.read());
            assertEquals(-1, input.read());
            assertEquals(0, input.read(new byte[1], 0, 0));
            assertEquals(1, availableCalls.get());
        }
        assertTrue(closed.get());
    }

    private ProcessHandle child() throws Exception {
        var pid = Long.parseLong(Files.readString(dir.resolve("child.pid")));
        return ProcessHandle.of(pid).orElseThrow();
    }

    private Execution start(String mode) throws Exception {
        var process = launch(mode);
        var executor = Executors.newSingleThreadExecutor(); // same platform threads as the agent
        var output = executor.submit(() -> {
            try (var input = new ProcessOutputInputStream(process)) {
                return input.readAllBytes();
            }
        });
        return new Execution(process, output, executor);
    }

    private Process launch(String mode) throws Exception {
        return new ProcessBuilder(javaCommand(), "-cp", System.getProperty("java.class.path"),
                Fixture.class.getName(), mode, dir.toString()).redirectErrorStream(true).start();
    }

    private static String javaCommand() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    private final class Execution implements AutoCloseable {
        private final Process process;
        private final java.util.concurrent.Future<byte[]> output;
        private final java.util.concurrent.ExecutorService executor;

        private Execution(Process process, java.util.concurrent.Future<byte[]> output,
                          java.util.concurrent.ExecutorService executor) {
            this.process = process;
            this.output = output;
            this.executor = executor;
        }

        @Override
        public void close() throws Exception {
            Files.writeString(dir.resolve("release"), "");
            if (Files.exists(dir.resolve("child.pid"))) {
                ProcessHandle.of(Long.parseLong(Files.readString(dir.resolve("child.pid"))))
                        .ifPresent(ProcessHandle::destroyForcibly);
            }
            process.destroyForcibly();
            output.cancel(true);
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    public static class Fixture {
        public static void main(String[] args) throws Exception {
            var mode = args[0];
            var dir = Path.of(args[1]);
            if (mode.equals("holder") || mode.equals("writer")) {
                Files.writeString(dir.resolve("ready"), "");
                var bytes = new byte[8192];
                Arrays.fill(bytes, (byte) 'x');
                while (!Files.exists(dir.resolve("release"))) {
                    if (mode.equals("writer")) {
                        System.out.write(bytes);
                        System.out.flush();
                    } else {
                        Thread.sleep(20);
                    }
                }
            } else if (mode.equals("large")) {
                var block = new byte[8192];
                for (var i = 0; i < block.length; i++) {
                    block[i] = (byte) (i % 251);
                }
                for (var i = 0; i < 2048; i++) {
                    (i % 2 == 0 ? System.out : System.err).write(block);
                }
                System.out.write(new byte[]{0, -1, 17});
                System.out.flush();
            } else {
                var child = new ProcessBuilder(javaCommand(), "-cp", System.getProperty("java.class.path"),
                        Fixture.class.getName(), mode.equals("busy") ? "writer" : "holder", dir.toString())
                        .inheritIO().start();
                Files.writeString(dir.resolve("child.pid"), Long.toString(child.pid()));
                while (!Files.exists(dir.resolve("ready"))) {
                    Thread.sleep(10);
                }
                System.out.print("runner-tail\n");
                System.out.flush();
            }
            System.exit(0);
        }
    }
}
