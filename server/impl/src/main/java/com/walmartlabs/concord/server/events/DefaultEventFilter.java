package com.walmartlabs.concord.server.events;

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

import com.walmartlabs.concord.common.Matcher;
import com.walmartlabs.concord.server.org.triggers.TriggerEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public final class DefaultEventFilter {

    private static final Logger log = LoggerFactory.getLogger(DefaultEventFilter.class);

    public static boolean filter(Map<String, Object> conditions, TriggerEntry t) {
        if (t.getConditions() == null || t.getConditions().isEmpty()) {
            return true;
        }

        try {
            return Matcher.matches(conditions, t.getConditions());
        } catch (Exception e) {
            log.warn("filter [{}, {}] -> error while matching events: {}", conditions, t, e.getMessage());
            return false;
        }
    }

    private DefaultEventFilter() {
    }
}
