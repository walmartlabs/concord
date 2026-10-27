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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/** Routes output from registered context class loaders without holding a stream lock while logging. */
final class SystemOutputBridge extends PrintStream {
    private static final Map<ClassLoader, Boolean> REGISTERED = Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Boolean> LOGGING = ThreadLocal.withInitial(() -> false);
    private static final StackWalker WALKER = StackWalker.getInstance();

    private final PrintStream original;
    private final boolean error;
    private final ThreadLocal<StringBuilder> pending = ThreadLocal.withInitial(StringBuilder::new);

    private SystemOutputBridge(PrintStream original, boolean error) {
        super(OutputStream.nullOutputStream(), false, StandardCharsets.UTF_8);
        this.original = original;
        this.error = error;
    }

    static synchronized void install() {
        REGISTERED.put(Thread.currentThread().getContextClassLoader(), Boolean.TRUE);
        if (!(System.out instanceof SystemOutputBridge)) {
            System.setOut(new SystemOutputBridge(System.out, false));
        }
        if (!(System.err instanceof SystemOutputBridge)) {
            System.setErr(new SystemOutputBridge(System.err, true));
        }
    }

    private static boolean registered() {
        synchronized (REGISTERED) {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            while (loader != null) {
                if (REGISTERED.containsKey(loader)) {
                    return true;
                }
                loader = loader.getParent();
            }
            return REGISTERED.containsKey(null);
        }
    }

    @Override
    public void write(int b) {
        original.write(b);
    }

    @Override
    public void write(byte[] bytes, int offset, int length) {
        original.write(bytes, offset, length);
    }

    private void emit(StringBuilder buffer, boolean line) {
        int length = buffer.length();
        // A flush must not separate a high surrogate from a low surrogate printed later.
        if (!line && length > 0 && Character.isHighSurrogate(buffer.charAt(length - 1))) {
            length--;
        }
        if (length == 0 && !line) {
            return;
        }
        int messageLength = length;
        if (line && messageLength > 0 && buffer.charAt(messageLength - 1) == '\r') {
            messageLength--;
        }
        String message = buffer.substring(0, messageLength);
        buffer.delete(0, length);
        String caller = WALKER.walk(frames -> frames.map(StackWalker.StackFrame::getClassName)
                .filter(name -> !name.equals(SystemOutputBridge.class.getName())
                        && !name.equals(PrintStream.class.getName())
                        && !name.startsWith("java.io."))
                .findFirst().orElse(SystemOutputBridge.class.getName()));
        LOGGING.set(true);
        try {
            Logger logger = LoggerFactory.getLogger(caller);
            if (error) {
                logger.error(message);
            } else {
                logger.info(message);
            }
        } finally {
            LOGGING.remove();
        }
    }

    @Override
    public void flush() {
        if (!LOGGING.get() && registered()) {
            emit(pending.get(), false);
        }
        original.flush();
    }

    @Override public boolean checkError() { flush(); return original.checkError(); }
    @Override public void close() { flush(); }

    private void text(String text) {
        if (LOGGING.get() || !registered()) {
            original.print(text);
            return;
        }
        StringBuilder buffer = pending.get();
        int start = 0;
        int newline;
        while ((newline = text.indexOf('\n', start)) >= 0) {
            buffer.append(text, start, newline);
            emit(buffer, true);
            start = newline + 1;
        }
        buffer.append(text, start, text.length());
    }

    private void text(char value) {
        if (LOGGING.get() || !registered()) {
            original.print(value);
            return;
        }
        StringBuilder buffer = pending.get();
        if (value == '\n') {
            emit(buffer, true);
        } else {
            buffer.append(value);
        }
    }

    @Override public void print(String value) { text(String.valueOf(value)); }
    @Override public void print(Object value) { print(String.valueOf(value)); }
    @Override public void print(boolean value) { print(String.valueOf(value)); }
    @Override public void print(char value) { text(value); }
    @Override public void print(char[] value) { print(String.valueOf(value)); }
    @Override public void print(int value) { print(String.valueOf(value)); }
    @Override public void print(long value) { print(String.valueOf(value)); }
    @Override public void print(float value) { print(String.valueOf(value)); }
    @Override public void print(double value) { print(String.valueOf(value)); }
    @Override public void println() { text(System.lineSeparator()); }
    @Override public void println(String value) { print(value); println(); }
    @Override public void println(Object value) { print(value); println(); }
    @Override public void println(boolean value) { print(value); println(); }
    @Override public void println(char value) { print(value); println(); }
    @Override public void println(char[] value) { print(value); println(); }
    @Override public void println(int value) { print(value); println(); }
    @Override public void println(long value) { print(value); println(); }
    @Override public void println(float value) { print(value); println(); }
    @Override public void println(double value) { print(value); println(); }
    @Override public PrintStream append(CharSequence value) { print(String.valueOf(value)); return this; }
    @Override public PrintStream append(CharSequence value, int start, int end) {
        CharSequence sequence = value == null ? "null" : value;
        return append(sequence.subSequence(start, end));
    }
    @Override public PrintStream append(char value) { print(value); return this; }
    @Override public PrintStream format(String format, Object... args) { return format(Locale.getDefault(), format, args); }
    @Override public PrintStream format(Locale locale, String format, Object... args) { print(String.format(locale, format, args)); return this; }
    @Override public PrintStream printf(String format, Object... args) { return format(format, args); }
    @Override public PrintStream printf(Locale locale, String format, Object... args) { return format(locale, format, args); }
}
