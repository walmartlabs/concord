package com.walmartlabs.concord.plugins.ansible;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.walmartlabs.concord.client2.ApiException;
import com.walmartlabs.concord.client2.ProcessEventRequest;
import com.walmartlabs.concord.client2.ProcessEventsApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Reads data recorded by concord_events.py and sends it to the Server.
 */
public class EventSender {

    private static final Logger log = LoggerFactory.getLogger(EventSender.class);

    private static final String EOL_MARKER = "<~EOL~>";

    private static final long NO_DATA_DELAY = 1000;
    private static final long API_ERROR_DELAY = 10000;
    private static final long MAX_BATCH_SIZE = 10;
    private static final long MAX_BATCH_AGE = 5000;

    private final boolean debug;
    private final UUID instanceId;
    private final Path eventsFile;
    private final ProcessEventsApi eventsApi;

    private final ObjectMapper objectMapper = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        return om;
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private volatile boolean stop = false;

    public EventSender(boolean debug, UUID instanceId, Path eventsFile, ProcessEventsApi eventsApi) {
        this.debug = debug;
        this.instanceId = instanceId;
        this.eventsFile = eventsFile;
        this.eventsApi = eventsApi;
    }

    public Future<?> start() {
        return executor.submit(this::doRun);
    }

    public void stop() {
        this.stop = true;
    }

    public void doRun() {
        if (debug) {
            log.info("run -> started...");
        }

        try (RandomAccessFile f = new RandomAccessFile(eventsFile.toFile(), "r")) {
            Batch batch = new Batch(instanceId, eventsApi);
            long t1 = System.currentTimeMillis();

            while (true) {
                String line = f.readLine();

                if (line == null || line.isEmpty()) {
                    if (stop) {
                        // looks like the end of the play

                        // don't stop until we reach the end of the file
                        if (Files.size(eventsFile) <= f.getFilePointer()) {
                            break;
                        }
                    }

                    // wait for more data
                    sleep(NO_DATA_DELAY);
                } else {
                    if (line.endsWith(EOL_MARKER)) {
                        String data = line.substring(0, line.length() - EOL_MARKER.length());
                        ProcessEventRequest req = objectMapper.readValue(data, ProcessEventRequest.class);
                        batch.add(req);
                    } else {
                        // partial line, re-read next time
                        f.seek(f.getFilePointer() - line.length());
                    }
                }

                long t2 = System.currentTimeMillis();
                if (batch.size() >= MAX_BATCH_SIZE || t2 - t1 >= MAX_BATCH_AGE) {
                    flush(batch);
                    t1 = t2;
                }
            }

            flush(batch);
        } catch (IOException e) {
            log.error("Error while reading the event file: {}", e.getMessage(), e);
        }

        if (debug) {
            log.info("run -> stopped...");
        }
    }

    private static void flush(Batch b) {
        try {
            b.flush();
        } catch (ApiException e) {
            log.warn("Error while sending the event to the server", e);
            sleep(API_ERROR_DELAY);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static class Batch {

        private final UUID instanceId;
        private final ProcessEventsApi eventsApi;

        private final List<ProcessEventRequest> items = new ArrayList<>();

        private Batch(UUID instanceId, ProcessEventsApi eventsApi) {
            this.instanceId = instanceId;
            this.eventsApi = eventsApi;
        }

        public void add(ProcessEventRequest req) {
            items.add(req);
        }

        public void flush() throws ApiException {
            eventsApi.batchEvent(instanceId, items);
            items.clear();
        }

        public int size() {
            return items.size();
        }
    }
}
