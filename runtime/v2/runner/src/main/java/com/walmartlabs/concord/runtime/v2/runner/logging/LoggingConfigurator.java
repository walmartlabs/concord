package com.walmartlabs.concord.runtime.v2.runner.logging;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import ch.qos.logback.classic.*;
import ch.qos.logback.classic.sift.SiftingAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.util.ContextInitializer;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.pattern.PatternLayoutEncoderBase;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LoggingConfigurator {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(LoggingConfigurator.class);

    private static final String PATTERN_PROPERTY_KEY = "OUTPUT_PATTERN";
    private static final String DEFAULT_PATTERN = "%date{YYYY-MM-dd'T'HH:mm:ss.SSSZ, UTC} [%-5level] %msg%n%rEx{full, com.sun, sun}";
    private static final String DEFAULT_ROOT_APPENDER_NAME = "STDOUT";
    private static final String PROCESS_LOGGER_NAME = "processLog";
    private static final String DEFAULT_PROCESS_LOG_APPENDER_NAME = "PROCESS_STDOUT";

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Map<String, Stats> statsHolder = new ConcurrentHashMap<>();

    public static void configure(UUID instanceId, String baseDir) {
        log.debug("Redirecting logging output into the segment log: {}", baseDir);

        Path dst = Paths.get(baseDir).resolve(instanceId.toString());
        if (!Files.exists(dst)) {
            try {
                Files.createDirectories(dst);
            } catch (IOException e) {
                throw new RuntimeException("Can't create the destination directory for the segmented log: " + baseDir, e);
            }
        }

        LoggerContext loggerContext = assertLoggerContext();

        String pattern = gerProperty(loggerContext, PATTERN_PROPERTY_KEY, DEFAULT_PATTERN);

        TaskDiscriminator discriminator = new TaskDiscriminator(instanceId);
        discriminator.start();

        SiftingAppender sa = new SiftingAppender();
        sa.setContext(loggerContext);
        sa.setName("SEGMENTED_LOG");
        sa.setDiscriminator(discriminator);
        sa.setAppenderFactory((context, discriminatingValue) -> {
            FileAppender<ILoggingEvent> fa = new FileAppender<>();
            fa.setContext(context);
            fa.setAppend(true);
            fa.setFile(String.format("%s/%s_%d.log",
                    dst.toAbsolutePath(), discriminatingValue, System.currentTimeMillis()));

            PatternLayoutEncoderBase<ILoggingEvent> encoder = new PatternLayoutEncoderBase<ILoggingEvent>() {
                @Override
                public void start() {
                    PatternLayout patternLayout = new CustomPatternLayout(discriminatingValue);
                    patternLayout.setContext(context);
                    patternLayout.setPattern(getPattern());
                    patternLayout.setOutputPatternAsHeader(outputPatternAsHeader);
                    patternLayout.start();

                    this.layout = patternLayout;
                    super.start();
                }
            };

            encoder.setContext(context);
            encoder.setPattern(pattern);
            encoder.start();

            fa.setEncoder(encoder);

            fa.start();
            return fa;
        });

        sa.start();

        loggerContext.resetTurboFilterList();

        Logger root = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        root.detachAppender(DEFAULT_ROOT_APPENDER_NAME);
        root.addAppender(sa);

        Logger processLog = loggerContext.getLogger(PROCESS_LOGGER_NAME);
        processLog.detachAppender(DEFAULT_PROCESS_LOG_APPENDER_NAME);
        processLog.addAppender(sa);
    }

    public static void reset() {
        LoggerContext loggerContext = assertLoggerContext();
        loggerContext.stop();

        ContextInitializer contextInitializer = new ContextInitializer(loggerContext);
        URL url = contextInitializer.findURLOfDefaultConfigurationFile(true);

        loggerContext.reset();

        try {
            contextInitializer.configureByResource(url);
        } catch (JoranException e) {
            throw new RuntimeException("Error while resetting the Logback configuration: " + e.getMessage(), e);
        }

        loggerContext.start();
    }

    private static LoggerContext assertLoggerContext() {
        ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();
        if (loggerFactory == null) {
            throw new IllegalStateException("Can't find the LoggerFactory.");
        }

        if (!(loggerFactory instanceof LoggerContext)) {
            throw new IllegalStateException("LoggerFactory is bound to something else than Logback's LoggingContext: " + loggerFactory.getClass() + "  Can't proceed.");
        }

        return (LoggerContext) loggerFactory;
    }

    private static String gerProperty(LoggerContext loggerContext, String key, String defaultValue) {
        String s = loggerContext.getProperty(key);
        if (s == null) {
            s = defaultValue;
        }
        return s;
    }

    private static String serializeStats(Stats stats) {
        try {
            return "\0<<<CONCORD_STATS:" + objectMapper.writeValueAsString(stats) + ">>>\n";
        } catch (IOException e) {
            throw new RuntimeException("Error while serializing a log segment's stats: " + e.getMessage(), e);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private static class Stats {

        private String status;
        private Integer errors;
        private Integer warnings;

        public void setStatus(String status) {
            this.status = status;
        }

        public String getStatus() {
            return status;
        }

        public Integer getErrors() {
            return errors;
        }

        public Integer getWarnings() {
            return warnings;
        }

        @JsonIgnore
        public Stats incError() {
            synchronized (this) {
                if (errors == null) {
                    errors = 0;
                }
                errors++;
                return this;
            }
        }

        @JsonIgnore
        public Stats incWarn() {
            synchronized (this) {
                if (warnings == null) {
                    warnings = 0;
                }
                warnings++;
                return this;
            }
        }
    }

    private static class CustomPatternLayout extends PatternLayout {

        private final String discriminatingValue;

        private CustomPatternLayout(String discriminatingValue) {
            this.discriminatingValue = discriminatingValue;
        }

        @Override
        public String doLayout(ILoggingEvent event) {
            Stats stats = null;

            Marker marker = event.getMarker();
            if (marker != null && marker.contains(ClassicConstants.FINALIZE_SESSION_MARKER)) {
                stats = statsHolder.remove(discriminatingValue);
                if (stats == null) {
                    stats = new Stats();
                }
                stats.setStatus("OK");
                return serializeStats(stats);
            }

            if (event.getLevel() == Level.ERROR) {
                stats = statsHolder.computeIfAbsent(discriminatingValue, s -> new Stats()).incError();
            } else if (event.getLevel() == Level.WARN) {
                stats = statsHolder.computeIfAbsent(discriminatingValue, s -> new Stats()).incWarn();
            }

            String result = super.doLayout(event);

            if (stats != null) {
                if (result == null) {
                    return serializeStats(stats);
                } else {
                    return result + "" + serializeStats(stats);
                }
            }

            return result;
        }
    }
}
