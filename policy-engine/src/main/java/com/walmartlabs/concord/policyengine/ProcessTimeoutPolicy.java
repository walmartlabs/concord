package com.walmartlabs.concord.policyengine;

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

import java.time.Duration;
import java.time.temporal.ChronoUnit;

public class ProcessTimeoutPolicy {

    private final ProcessTimeoutRule rule;

    public ProcessTimeoutPolicy(ProcessTimeoutRule rule) {
        this.rule = rule;
    }

    public CheckResult<ProcessTimeoutRule, Object> check(Object timeout) {
        if (rule == null) {
            return CheckResult.success();
        }

        Long processTimeout = parseTimeout(timeout);
        if (processTimeout == null) {
            return CheckResult.success();
        }

        if (processTimeout >= parseTimeout(rule.max())) {
            return CheckResult.error(new CheckResult.Item<>(rule, timeout));
        }
        return CheckResult.success();
    }

    private static Long parseTimeout(Object timeout) {
        if (timeout == null) {
            return null;
        }

        if (timeout instanceof String) {
            Duration duration = Duration.parse((CharSequence) timeout);
            return duration.get(ChronoUnit.SECONDS);
        }

        throw new IllegalArgumentException("Invalid process timeout value type: expected an ISO-8601 value, got: " + timeout);
    }
}
