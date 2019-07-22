package com.walmartlabs.concord.plugins.sleep;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.client.ApiClientConfiguration;
import com.walmartlabs.concord.client.ApiClientFactory;
import com.walmartlabs.concord.client.ClientUtils;
import com.walmartlabs.concord.client.ProcessApi;
import com.walmartlabs.concord.sdk.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.sdk.ContextUtils.getBoolean;
import static com.walmartlabs.concord.sdk.ContextUtils.getNumber;

@Named("sleep")
public class SleepTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(SleepTask.class);

    private static final int RETRY_COUNT = 3;
    private static final long RETRY_INTERVAL = 5000;

    private static final String RESUME_EVENT_NAME = "sleepTask";
    private static final String DATETIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSX";

    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(DATETIME_PATTERN).withZone(ZoneOffset.UTC);
    private final ApiClientFactory apiClientFactory;

    @InjectVariable(Constants.Context.CONTEXT_KEY)
    private Context ctx;

    @Inject
    public SleepTask(ApiClientFactory apiClientFactory) {
        this.apiClientFactory = apiClientFactory;
    }

    public void ms(long t) {
        sleep(t);
    }

    public void spam(String message, int delay, int count) {
        try {
            for (int i = 0; i < count; i++) {
                log.info("MESSAGE: {}", message);
                Thread.sleep(delay);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void execute(Context ctx) throws Exception {
        Number duration = getNumber(ctx, "duration", null);
        Instant until = getUntil(ctx, "until");

        if (duration == null && until == null) {
            log.error("Invalid sleep task input parameters: 'duration' or 'until' must be specified");
            throw new IllegalArgumentException("Invalid arguments");
        }

        if (duration != null && until != null) {
            log.error("Invalid sleep task input parameters: 'duration' and 'until' are mutually exclusive");
            throw new IllegalArgumentException("Invalid arguments");
        }

        boolean suspend = getBoolean(ctx, "suspend", false);
        if (suspend) {
            Instant sleepUntil = toSleepUntil(duration, until);
            if (sleepUntil.isBefore(Instant.now())) {
                log.warn("Skipping the sleep, the specified datetime is in the past: {}", sleepUntil);
                return;
            }
            log.info("Sleeping until {}...", sleepUntil);
            suspend(sleepUntil);
        } else {
            long sleepTime = toSleepDuration(duration, until);
            if (sleepTime <= 0) {
                return;
            }
            log.info("Sleeping for {}ms", sleepTime);
            sleep(sleepTime);
        }
    }

    private void suspend(Instant until) throws ApiException {
        Map<String, Object> condition = new HashMap<>();
        condition.put("type", "PROCESS_SLEEP");
        condition.put("until", dateFormatter.format(until));
        condition.put("reason", "Waiting till " + until);
        condition.put("resumeEvent", RESUME_EVENT_NAME);

        ProcessApi api = new ProcessApi(apiClientFactory.create(ApiClientConfiguration.builder()
                .context(ctx)
                .build()));

        ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL, () -> {
            api.setWaitCondition(ContextUtils.getTxId(ctx), condition);
            return null;
        });

        ctx.suspend(RESUME_EVENT_NAME);
    }

    private static void sleep(long t) {
        try {
            Thread.sleep(t);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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

    private static Instant getUntil(Context ctx, String name) {
        Object value = ctx.getVariable(name);
        if (value == null) {
            return null;
        }

        if (value instanceof Date) {
            return ((Date) value).toInstant();
        }

        if (value instanceof String) {
            try {
                return ZonedDateTime.parse((CharSequence) value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Invalid datetime string. Expected " + DATETIME_PATTERN +
                        " (e.g. " + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now()) + ") got: " + value);
            }
        }

        throw new IllegalArgumentException("Invalid variable '" + name + "' type, expected: date/string, got: " + value.getClass());
    }
}
