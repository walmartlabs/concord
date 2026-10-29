package com.walmartlabs.concord.server.sdk.events;

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

import com.walmartlabs.concord.server.sdk.PartialProcessKey;

import java.util.List;
import java.util.Map;

/**
 * Submits normalized external events in the current authenticated server request
 * context. Submission waits for enqueue, not workflow completion; it does not
 * bypass server security.
 */
public interface ExternalEventService {

    /** Returns whether external events for the source are disabled. */
    boolean isDisabled(String source);

    /**
     * Records a raw request through the server audit machinery, subject to global
     * audit configuration. The caller controls source-specific audit enablement and
     * calls this at most once per incoming request. Submission does not audit or retry it.
     * Payload maps may contain null values and are treated as read-only.
     */
    void audit(String source, String eventId, Map<String, Object> payload);

    /**
     * Rechecks disabled sources and collects matches for all events before enqueuing
     * any process, preserving event order and DAO trigger order. Returns successful
     * process keys only, using the executor's existing failure policy. An empty list
     * returns an empty list; null lists and entries are programming errors. No
     * transaction, retry, deduplication or rollback is provided.
     */
    List<PartialProcessKey> submit(List<ExternalEvent> events);
}
