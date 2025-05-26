package com.walmartlabs.concord.server.process;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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

import javax.inject.Inject;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Objects.requireNonNull;

/**
 * Tracks the number of STARTING or RESUMING processes for the local concord-server instance
 * (as opposed to counting statuses in PROCESS_QUEUE which will return the number of processes
 * across all server instances).
 */
public class InflightProcessTracker {

    private final AtomicInteger starting = new AtomicInteger(0);
    private final AtomicInteger resuming = new AtomicInteger(0);

    public int getStarting() {
        return starting.get();
    }

    public int getResuming() {
        return resuming.get();
    }

    public static class StartBeginTracker implements Runnable {

        private final InflightProcessTracker tracker;

        @Inject
        public StartBeginTracker(InflightProcessTracker tracker) {
            this.tracker = requireNonNull(tracker);
        }

        @Override
        public void run() {
            tracker.starting.incrementAndGet();
        }
    }

    public static class StartDoneTracker implements Runnable {

        private final InflightProcessTracker tracker;

        @Inject
        public StartDoneTracker(InflightProcessTracker tracker) {
            this.tracker = requireNonNull(tracker);
        }

        @Override
        public void run() {
            tracker.starting.decrementAndGet();
        }
    }

    public static class ResumeBeginTracker implements Runnable {

        private final InflightProcessTracker tracker;

        @Inject
        public ResumeBeginTracker(InflightProcessTracker tracker) {
            this.tracker = requireNonNull(tracker);
        }

        @Override
        public void run() {
            tracker.resuming.incrementAndGet();
        }
    }

    public static class ResumeDoneTracker implements Runnable {

        private final InflightProcessTracker tracker;

        @Inject
        public ResumeDoneTracker(InflightProcessTracker tracker) {
            this.tracker = requireNonNull(tracker);
        }

        @Override
        public void run() {
            tracker.resuming.decrementAndGet();
        }
    }
}
