package com.walmartlabs.concord.plugins.sleep;

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

import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

public class SleepTaskCommon {

    private static final Logger log = LoggerFactory.getLogger(SleepTaskCommon.class);

    public static void sleep(long t) {
        try {
            Thread.sleep(t);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private final Supplier<Suspender> suspender;

    public SleepTaskCommon(Supplier<Suspender> suspender) {
        this.suspender = suspender;
    }

    public TaskResult execute(TaskParams input) throws Exception {
        Number duration = input.duration();
        Instant until = input.until();

        validateInputParams(duration, until);

        if (input.suspend()) {
            Instant sleepUntil = toSleepUntil(duration, until);
            if (sleepUntil.isBefore(Instant.now())) {
                log.warn("Skipping the sleep, the specified datetime is in the " +
                        "past: {}", sleepUntil);
                return TaskResult.success();
            }
            log.info("Sleeping until {}...", sleepUntil);
            return suspender.get().suspend(sleepUntil);
        } else {
            long sleepTime = toSleepDuration(duration, until);
            if (sleepTime <= 0) {
                log.warn("Skipping the sleep, the specified datetime is either negative " +
                        "or is in the past: {}", sleepTime);
                return TaskResult.success();
            }
            log.info("Sleeping for {}ms", sleepTime);
            sleep(sleepTime);
            return TaskResult.success();
        }
    }

    private static long toSleepDuration(Number duration, Instant until) {
        if (duration != null) {
            return duration.longValue() * 1000;
        }

        return Duration.between(Instant.now(), until).toMillis();
    }

    private static Instant toSleepUntil(Number duration, Instant until) {
        if (until != null) {
            return until;
        }

        return Instant.now().plusSeconds(duration.longValue());
    }

    public static void validateInputParams(Number duration, Instant until) {
        if (duration == null && until == null) {
            log.error("Invalid sleep task input parameters: 'duration' or 'until' must be specified");
            throw new IllegalArgumentException("Invalid arguments");
        }

        if (duration != null && until != null) {
            log.error("Invalid sleep task input parameters: 'duration' and 'until' are mutually exclusive");
            throw new IllegalArgumentException("Invalid arguments");
        }
    }
}
