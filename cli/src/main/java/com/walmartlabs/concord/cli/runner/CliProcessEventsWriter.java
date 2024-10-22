package com.walmartlabs.concord.cli.runner;

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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.client2.ProcessEventEntry;
import com.walmartlabs.concord.client2.ProcessEventRequest;
import com.walmartlabs.concord.runtime.common.injector.InstanceId;
import com.walmartlabs.concord.runtime.v2.runner.ProcessEventWriter;
import com.walmartlabs.concord.runtime.v2.sdk.WorkingDirectory;
import com.walmartlabs.concord.svm.ExecutionListener;
import com.walmartlabs.concord.svm.Frame;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.State;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class CliProcessEventsWriter implements ProcessEventWriter, ExecutionListener {

    private static final TypeReference<List<ProcessEventEntry>> EVENTS_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final Path outputFile;
    private volatile boolean isFirstEvent = true;

    @Inject
    public CliProcessEventsWriter(ObjectMapper objectMapper, InstanceId instanceId, WorkingDirectory workingDirectory) {
        this.objectMapper = objectMapper;
        this.outputFile = workingDirectory.getValue()
                .resolve("events")
                .resolve(instanceId.getValue() + ".events.json");

        try {
            Files.createDirectories(outputFile.getParent());
        } catch (IOException e) {
            throw new RuntimeException("Can't create events directory", e);
        }
    }

    public List<ProcessEventEntry> events() {
        try {
            return objectMapper.readValue(outputFile.toFile(), EVENTS_TYPE);
        } catch (Exception e) {
            throw new RuntimeException(String.format("Can't read events from '%s'", outputFile), e);
        }
    }

    @Override
    public void write(ProcessEventRequest event) {
        writeEvent(event);
    }

    @Override
    public void write(List<ProcessEventRequest> events) {
        events.forEach(this::write);
    }

    @Override
    public void beforeProcessStart(Runtime runtime, State state) {
        writeArrayStartIfNeeded(outputFile);
    }

    @Override
    public void beforeProcessResume(Runtime runtime, State state) {
        writeArrayStartIfNeeded(outputFile);
    }

    @Override
    public void afterProcessEnds(Runtime runtime, State state, Frame lastFrame) {
        writeArrayEnd(outputFile);
    }

    @Override
    public void onProcessError(Runtime runtime, State state, Exception e) {
        writeArrayEnd(outputFile);
    }

    private synchronized void writeEvent(ProcessEventRequest event) {
        try (var out = Files.newOutputStream(outputFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            if (!isFirstEvent) {
                out.write(",".getBytes(StandardCharsets.UTF_8));
            }

            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(out, event);

            isFirstEvent = false;
        } catch (IOException e) {
            throw new RuntimeException("Error writing event", e);
        }
    }

    private static void writeArrayEnd(Path outputFile) {
        try (var out = Files.newOutputStream(outputFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            out.write("]".getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(String.format("Error writing to events file '%s", outputFile), e);
        }
    }

    private static void writeArrayStartIfNeeded(Path outputFile) {
        try (var out = Files.newOutputStream(outputFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            if (Files.size(outputFile) == 0) {
                out.write("[".getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new RuntimeException(String.format("Error writing to events file '%s'", outputFile), e);
        }
    }
}
