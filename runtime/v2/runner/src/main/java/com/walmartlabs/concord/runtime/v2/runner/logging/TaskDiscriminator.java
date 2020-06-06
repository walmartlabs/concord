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

public class TaskDiscriminator extends AbstractDiscriminator<ILoggingEvent> {

    public static final String UNSEGMENTED_LOG = "system";

    /**
     * The segment ID lookup's maximum depth (limits nesting of {@link ThreadGroup}s).
     */
    private static final int MAX_DEPTH = 100;

    @Override
    public String getDiscriminatingValue(ILoggingEvent iLoggingEvent) {
        int depth = 0;

        ThreadGroup g = Thread.currentThread().getThreadGroup();
        while (true) {
            if (g instanceof TaskThreadGroup) {
                TaskThreadGroup ttg = (TaskThreadGroup)g;
                return ttg.getSegmentId() + "-" + ttg.getName();
            }

            if (g.getParent() == null) {
                break;
            }

            g = g.getParent();
            depth++;

            if (depth >= MAX_DEPTH) {
                throw new IllegalStateException("Maximum ThreadGroup nesting limit is reached. " +
                        "This is most likely a bug in the runtime and/or a plugin.");
            }
        }

        return UNSEGMENTED_LOG;
    }

    @Override
    public String getKey() {
        return "_concord_segment";
    }
}
