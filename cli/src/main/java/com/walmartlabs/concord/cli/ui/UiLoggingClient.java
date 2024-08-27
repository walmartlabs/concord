package com.walmartlabs.concord.cli.ui;

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

import com.walmartlabs.concord.runtime.v2.runner.logging.LoggingClient;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Objects.requireNonNull;

public class UiLoggingClient implements LoggingClient {

    private final Ui ui;
    private final AtomicLong counter = new AtomicLong(1);

    public UiLoggingClient(Ui ui) {
        this.ui = requireNonNull(ui);
    }

    @Override
    public long createSegment(UUID correlationId, String name) {
        var segmentId = counter.getAndIncrement();
        ui.onCreateLogSegment(segmentId, name);
        return segmentId;
    }
}
