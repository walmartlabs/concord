package com.walmartlabs.concord.runtime.v2.runner.logging;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.sift.AbstractDiscriminator;

/**
 * Returns a Logback's discriminator value based on the current log "segment".
 */
public class SegmentDiscriminator extends AbstractDiscriminator<ILoggingEvent> {

    private static final String SYSTEM_SEGMENT_ID = "0";

    @Override
    public String getDiscriminatingValue(ILoggingEvent iLoggingEvent) {
        Long segmentId = LogUtils.getSegmentId();
        if (segmentId == null) {
            return SYSTEM_SEGMENT_ID;
        }

        return String.valueOf(segmentId);
    }

    @Override
    public String getKey() {
        return "_concord_segment";
    }
}
