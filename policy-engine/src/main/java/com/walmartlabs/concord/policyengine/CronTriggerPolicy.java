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
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

public class CronTriggerPolicy {

    private final CronTriggerRule rule;

    public CronTriggerPolicy(CronTriggerRule rule) {
        this.rule = rule;
    }

    public CheckResult<CronTriggerRule, Duration> check(OffsetDateTime fireAt, OffsetDateTime nexFireAt) {
        if (rule == null) {
            return CheckResult.success();
        }

        Duration interval = Duration.between(fireAt.toLocalTime(), nexFireAt.toLocalTime());

        if (TimeUnit.SECONDS.toMillis(rule.minInterval()) > interval.toMillis()) {
            return CheckResult.error(new CheckResult.Item<>(rule, interval));
        }

        return CheckResult.success();
    }
}
