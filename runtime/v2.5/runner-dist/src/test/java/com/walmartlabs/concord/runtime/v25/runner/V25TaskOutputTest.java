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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.read.ListAppender;
import com.walmartlabs.concord.runtime.v2.sdk.SensitiveDataHolder;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V25TaskOutputTest {

    private static final Object OUTPUT_LOCK = new Object();

    @Test
    void retainsCaptureAcrossNestedTaskInvocation() {
        synchronized (OUTPUT_LOCK) {
            var logger = taskLogger();
            var appender = new ListAppender<ILoggingEvent>();
            appender.start();
            logger.addAppender(appender);
            var additive = logger.isAdditive();
            var level = logger.getLevel();
            logger.setAdditive(false);
            logger.setLevel(Level.INFO);
            V25LogLayout.configure(sensitive("secret"), null);
            V25TaskOutput.install();
            try {
                V25TaskOutput.enter();
                System.out.println("outer secret before nested task");
                V25TaskOutput.enter();
                System.out.println("nested secret");
                V25TaskOutput.leave();
                System.out.println("outer secret after nested task");
                V25TaskOutput.leave();

                assertEquals(List.of("outer secret before nested task", "nested secret", "outer secret after nested task"),
                        appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList());
                var layout = layout();
                for (var event : appender.list) {
                    var masked = layout.doLayout(event);
                    assertTrue(masked.contains("******"));
                    assertFalse(masked.contains("secret"));
                }
            } finally {
                logger.detachAppender(appender);
                logger.setAdditive(additive);
                logger.setLevel(level);
            }
        }
    }

    @Test
    void flushesTrailingTaskOutputBeforeClosingSegmentScope() {
        synchronized (OUTPUT_LOCK) {
            var logger = taskLogger();
            var output = new ByteArrayOutputStream();
            var appender = encodedAppender(output);
            appender.start();
            logger.addAppender(appender);
            var additive = logger.isAdditive();
            var level = logger.getLevel();
            logger.setAdditive(false);
            logger.setLevel(Level.INFO);
            V25TaskOutput.install();
            V25LogEncoder.segmented(true);
            try {
                var segment = V25LogEncoder.scope(42L);
                V25TaskOutput.enter();
                System.out.print("trailing task output");

                RunnerTaskEventHook.leaveTaskOutput(segment);

                var encoded = output.toString(StandardCharsets.UTF_8);
                assertTrue(encoded.contains("|42|"));
                assertTrue(encoded.contains("trailing task output"));
            } finally {
                logger.detachAppender(appender);
                logger.setAdditive(additive);
                V25LogEncoder.segmented(false);
                logger.setLevel(level);
            }
        }
    }

    private static Logger taskLogger() {
        return (Logger) LoggerFactory.getLogger("v25.task.stdout");
    }

    private static V25LogLayout layout() {
        var layout = new V25LogLayout();
        layout.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        layout.setPattern("%msg");
        layout.start();
        return layout;
    }

    private static OutputStreamAppender<ILoggingEvent> encodedAppender(ByteArrayOutputStream output) {
        var context = (LoggerContext) LoggerFactory.getILoggerFactory();
        var encoder = new V25LogEncoder();
        encoder.setContext(context);
        encoder.setLayout(layout());
        encoder.start();
        var appender = new OutputStreamAppender<ILoggingEvent>();
        appender.setContext(context);
        appender.setEncoder(encoder);
        appender.setOutputStream(output);
        return appender;
    }

    private static SensitiveDataHolder sensitive(String... values) {
        return new SensitiveValues(Set.of(values));
    }

    private static final class SensitiveValues implements SensitiveDataHolder {
        private final Set<String> values;

        private SensitiveValues(Set<String> values) {
            this.values = new LinkedHashSet<>(values);
        }

        @Override
        public Set<String> get() {
            return values;
        }

        @Override
        public void add(String sensitiveData) {
            values.add(sensitiveData);
        }

        @Override
        public void addAll(Collection<String> sensitiveData) {
            values.addAll(sensitiveData);
        }
    }
}
