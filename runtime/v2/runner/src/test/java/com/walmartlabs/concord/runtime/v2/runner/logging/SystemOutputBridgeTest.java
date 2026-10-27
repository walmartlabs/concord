package com.walmartlabs.concord.runtime.v2.runner.logging;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("system streams")
class SystemOutputBridgeTest {
    @Test
    void routesLinesPerThreadAndPreservesOriginalStreams() throws Exception {
        PrintStream savedOut = System.out;
        PrintStream savedErr = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        Level savedLevel = root.getLevel();
        List<String> events = new CopyOnWriteArrayList<>();
        AppenderBase<ILoggingEvent> appender = new AppenderBase<>() {
            @Override protected void append(ILoggingEvent event) {
                LogContext context = LogUtils.getContext();
                events.add(event.getLevel() + ":" + (context == null ? "none" : context.segmentName()) + ":" + event.getFormattedMessage());
                // An appender that writes to the current stream must not re-enter logging.
                if (event.getFormattedMessage().equals("recursive")) {
                    System.out.println("appender output");
                }
            }
        };
        appender.start();
        root.addAppender(appender);
        root.setLevel(Level.INFO);
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
            SystemOutputBridge.install();
            PrintStream bridgeOut = System.out;
            PrintStream bridgeErr = System.err;
            SystemOutputBridge.install();
            assertSame(bridgeOut, System.out);
            assertSame(bridgeErr, System.err);

            LogContext context = LogContext.builder().segmentName("segment").correlationId(UUID.randomUUID())
                    .redirectSystemOutAndErr(true).build();
            Thread thread = new Thread(new LogContextThreadGroup(context), () -> {
                System.out.print("part");
                System.out.print("ial\n");
                System.out.println("é");
                System.out.println();
                System.out.append(new CharSequence() {
                    @Override public int length() { return 2; }
                    @Override public char charAt(int index) { return "ab".charAt(index); }
                    @Override public CharSequence subSequence(int start, int end) { return "selected"; }
                    @Override public String toString() { return "different"; }
                }, 0, 1).println();
                System.out.append(null, 1, 3).println();
                System.out.print('\uD83D');
                System.out.println('\uDE00');
                System.out.append('\uD83D');
                System.out.flush();
                System.out.format("%s", "\uDE00").println();
                System.err.println("failure");
                System.out.print("flushed");
                System.out.flush();
                System.out.println("recursive");
            });
            thread.start();
            thread.join();
            assertThrows(NullPointerException.class, () -> System.out.print((char[]) null));

            Thread unregistered = new Thread(() -> System.out.println("outside"));
            unregistered.setContextClassLoader(new ClassLoader(null) {});
            unregistered.start();
            unregistered.join();

            assertTrue(events.contains("INFO:segment:partial"));
            assertTrue(events.contains("INFO:segment:é"));
            assertTrue(events.contains("INFO:segment:"));
            assertTrue(events.contains("INFO:segment:selected"));
            assertTrue(events.contains("INFO:segment:ul"));
            assertEquals(2, events.stream().filter(e -> e.equals("INFO:segment:😀")).count());
            assertTrue(events.contains("ERROR:segment:failure"));
            assertTrue(events.contains("INFO:segment:flushed"));
            assertTrue(events.contains("INFO:segment:recursive"));
            assertFalse(events.stream().anyMatch(e -> e.endsWith(":outside") || e.endsWith(":appender output")));
            assertTrue(out.toString(StandardCharsets.UTF_8).contains("outside"));
            assertTrue(out.toString(StandardCharsets.UTF_8).contains("appender output"));
        } finally {
            System.setOut(savedOut);
            System.setErr(savedErr);
            root.setLevel(savedLevel);
            root.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void rawWritesPassThroughWithoutAffectingPendingText() {
        PrintStream savedOut = System.out;
        PrintStream savedErr = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        Level savedLevel = root.getLevel();
        List<String> events = new CopyOnWriteArrayList<>();
        AppenderBase<ILoggingEvent> appender = new AppenderBase<>() {
            @Override protected void append(ILoggingEvent event) {
                events.add(event.getLevel() + ":" + event.getFormattedMessage());
            }
        };
        appender.start();
        root.addAppender(appender);
        root.setLevel(Level.INFO);
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
            SystemOutputBridge.install();

            System.out.print("pending:\uD83D");
            System.err.print("error:");
            byte[] bytes = {'x', (byte) 0xc3, (byte) 0xa9, '\n', 'y'};
            System.out.write(bytes, 1, 2);
            System.out.write('\n');
            System.out.write(0xc3);
            System.err.write(bytes, 2, 2);
            System.err.write(0xff);

            assertArrayEquals(new byte[]{(byte) 0xc3, (byte) 0xa9, '\n', (byte) 0xc3}, out.toByteArray());
            assertArrayEquals(new byte[]{(byte) 0xa9, '\n', (byte) 0xff}, err.toByteArray());
            assertTrue(events.isEmpty());

            System.out.println('\uDE00');
            System.err.println("done");
            assertEquals(List.of("INFO:pending:😀", "ERROR:error:done"), events);
            assertArrayEquals(new byte[]{(byte) 0xc3, (byte) 0xa9, '\n', (byte) 0xc3}, out.toByteArray());
            assertArrayEquals(new byte[]{(byte) 0xa9, '\n', (byte) 0xff}, err.toByteArray());
        } finally {
            System.setOut(savedOut);
            System.setErr(savedErr);
            root.setLevel(savedLevel);
            root.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void partialLinesFromDifferentThreadsDoNotMix() throws Exception {
        PrintStream savedOut = System.out;
        PrintStream savedErr = System.err;
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        Level savedLevel = root.getLevel();
        List<String> messages = new CopyOnWriteArrayList<>();
        AppenderBase<ILoggingEvent> appender = new AppenderBase<>() {
            @Override protected void append(ILoggingEvent event) {
                if (event.getLevel() == Level.INFO) messages.add(event.getFormattedMessage());
            }
        };
        appender.start();
        root.addAppender(appender);
        root.setLevel(Level.INFO);
        try {
            System.setOut(new PrintStream(new ByteArrayOutputStream()));
            System.setErr(new PrintStream(new ByteArrayOutputStream()));
            SystemOutputBridge.install();
            java.util.concurrent.CountDownLatch written = new java.util.concurrent.CountDownLatch(2);
            Runnable print = () -> {
                String name = Thread.currentThread().getName();
                System.out.print(name);
                System.out.print(name.equals("first") ? '\uD83D' : '\uD83C');
                written.countDown();
                try { written.await(); } catch (InterruptedException e) { throw new AssertionError(e); }
                System.out.println(name.equals("first") ? '\uDE00' : '\uDF0D');
            };
            Thread a = new Thread(print, "first");
            Thread b = new Thread(print, "second");
            a.start(); b.start(); a.join(); b.join();
            assertTrue(messages.contains("first😀"));
            assertTrue(messages.contains("second🌍"));
        } finally {
            System.setOut(savedOut);
            System.setErr(savedErr);
            root.setLevel(savedLevel);
            root.detachAppender(appender);
            appender.stop();
        }
    }
}
