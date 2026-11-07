package com.walmartlabs.concord.server.metrics;

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

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.walmartlabs.concord.server.process.queue.ProcessStatusListener;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Fixed, identifier-free metric families. Eagerly bound so idle nodes export zeros too.
 * Counters observe successful calls/notifications; a later transaction rollback can still occur.
 */
@Singleton
public class ProcessQueueMetrics implements ProcessStatusListener {

    public enum PassOutcome { NO_CREDITS, NO_CANDIDATES, NO_MATCH, MATCHED }
    public enum Rejection { NO_CREDIT_MATCH, FILTER_CONCURRENT, FILTER_EXCLUSIVE, FILTER_WAIT, FILTER_OTHER }
    public enum ResponseOutcome { SENT, SEND_FAILED, PREPARE_FAILED }
    public enum Resolution { RESUMED, UPDATED, VERSION_CONFLICT, ERROR }
    public enum EnqueuedSource { ENQUEUE, RESUME, WAIT_ACTION, STATUS_API }
    public enum ChannelEvent { CONNECT, DISCONNECT, DUPLICATE_REQUEST }

    public final Map<PassOutcome, Counter> passes;
    public final Map<Rejection, Counter> rejections;
    public final Map<ResponseOutcome, Counter> responses;
    public final Map<Resolution, Counter> resolutions;
    public final Map<EnqueuedSource, Counter> enqueued;
    public final Map<ChannelEvent, Counter> channelEvents;
    private final Map<ProcessStatus, Counter> transitions;

    public final Milliseconds enqueuedAge;
    public final Milliseconds creditAge;
    public final Milliseconds sleepOvershoot;
    public final Milliseconds finishDelay;
    public final Milliseconds watchdogInterval;
    public final Histogram candidates;
    public final Histogram pages;
    public final Histogram credits;
    public final Timer lockWait;
    public final Timer lockHold;

    @Inject
    public ProcessQueueMetrics(MetricRegistry registry) {
        passes = counters(registry, "process-queue-dispatcher-pass-", PassOutcome.class);
        rejections = counters(registry, "process-queue-dispatcher-rejection-", Rejection.class);
        responses = counters(registry, "process-queue-dispatcher-response-", ResponseOutcome.class);
        resolutions = counters(registry, "process-wait-watchdog-resolution-", Resolution.class);
        enqueued = counters(registry, "process-queue-enqueued-", EnqueuedSource.class);
        channelEvents = counters(registry, "agent-channel-", ChannelEvent.class);
        transitions = counters(registry, "process-queue-status-transition-", ProcessStatus.class);
        enqueuedAge = new Milliseconds(registry, "process-queue-dispatcher-enqueued-age-ms");
        creditAge = new Milliseconds(registry, "process-queue-dispatcher-credit-age-ms");
        sleepOvershoot = new Milliseconds(registry, "process-wait-sleep-overshoot-ms");
        finishDelay = new Milliseconds(registry, "process-wait-finish-delay-ms");
        watchdogInterval = new Milliseconds(registry, "process-wait-watchdog-pass-interval-ms");
        candidates = registry.histogram("process-queue-dispatcher-candidates-per-pass");
        pages = registry.histogram("process-queue-dispatcher-pages-per-pass");
        credits = registry.histogram("process-queue-dispatcher-credits-per-pass");
        lockWait = registry.timer("process-queue-dispatcher-lock-wait");
        lockHold = registry.timer("process-queue-dispatcher-lock-hold");
    }

    @Override
    public void onStatusChange(DSLContext tx, ProcessKey processKey, ProcessStatus status) {
        transitions.get(status).inc();
    }

    private static <E extends Enum<E>> Map<E, Counter> counters(MetricRegistry registry, String prefix, Class<E> type) {
        var result = new EnumMap<E, Counter>(type);
        for (var value : type.getEnumConstants()) {
            result.put(value, registry.counter(prefix + value.name().toLowerCase(Locale.ROOT).replace('_', '-')));
        }
        return result;
    }

    public static long elapsedMillis(long startNanos, long nowNanos) {
        return TimeUnit.NANOSECONDS.toMillis(Math.max(0, nowNanos - startNanos));
    }

    public static long ageMillis(OffsetDateTime since, OffsetDateTime now) {
        return Math.max(0, Duration.between(since, now).toMillis());
    }

    /** Histograms retain the existing millisecond convention; totals allow sums across nodes. */
    public static class Milliseconds {
        private final Histogram histogram;
        private final Counter total;

        private Milliseconds(MetricRegistry registry, String name) {
            histogram = registry.histogram(name);
            total = registry.counter(name + "-total");
        }

        public void update(long millis) {
            var value = Math.max(0, millis);
            histogram.update(value);
            total.inc(value);
        }
    }
}
