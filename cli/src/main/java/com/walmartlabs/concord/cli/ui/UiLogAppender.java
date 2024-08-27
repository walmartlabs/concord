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

import com.walmartlabs.concord.runtime.common.logger.LogAppender;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentStats;

import java.util.UUID;

import static java.util.Objects.requireNonNull;

public class UiLogAppender implements LogAppender {

    private final Ui ui;

    public UiLogAppender(Ui ui) {
        this.ui = requireNonNull(ui);
    }

    @Override
    public void appendLog(UUID instanceId, byte[] ab) {
        ui.appendLog(0, ab);
    }

    @Override
    public boolean appendLog(UUID instanceId, long segmentId, byte[] ab) {
        ui.appendLog(segmentId, ab);
        return true;
    }

    @Override
    public boolean updateSegment(UUID instanceId, long segmentId, LogSegmentStats stats) {
        // TODO
        return true;
    }
}
