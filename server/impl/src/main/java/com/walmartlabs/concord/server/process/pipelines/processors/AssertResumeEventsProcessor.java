package com.walmartlabs.concord.server.process.pipelines.processors;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessKey;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class AssertResumeEventsProcessor implements PayloadProcessor {

    private final ProcessLogManager logManager;
    private final ProcessStateManager stateManager;

    @Inject
    public AssertResumeEventsProcessor(ProcessLogManager logManager,
                                       ProcessStateManager stateManager) {
        this.logManager = logManager;
        this.stateManager = stateManager;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        ProcessKey processKey = payload.getProcessKey();

        Set<String> events = payload.getHeader(Payload.RESUME_EVENTS, Collections.emptySet());
        if (events.isEmpty()) {
            logManager.warn(processKey, "Resuming with empty events");
            return chain.process(payload);
        }

        Set<String> expectedEvents = getResumeEvents(processKey);

        Set<String> unexpectedEvents = new HashSet<>(events);
        unexpectedEvents.removeAll(expectedEvents);

        if (!unexpectedEvents.isEmpty()) {
            logManager.warn(processKey, "Unexpected resuming events: {}, expected: {}", unexpectedEvents, expectedEvents);
            throw new InvalidProcessStateException("Unexpected 'resume' events: " + unexpectedEvents);
        }

        return chain.process(payload);
    }

    private Set<String> getResumeEvents(PartialProcessKey processKey) {
        String path = ProcessStateManager.path(Constants.Files.JOB_ATTACHMENTS_DIR_NAME,
                        Constants.Files.JOB_STATE_DIR_NAME,
                        Constants.Files.SUSPEND_MARKER_FILE_NAME);

        return stateManager.get(processKey, path, AssertResumeEventsProcessor::deserialize)
                .orElse(Set.of());
    }

    private static Optional<Set<String>> deserialize(InputStream in) {
        Set<String> result = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.add(line);
            }
            return Optional.of(result);
        } catch (IOException e) {
            throw new RuntimeException("Error while deserializing a resume events: " + e.getMessage(), e);
        }
    }
}
