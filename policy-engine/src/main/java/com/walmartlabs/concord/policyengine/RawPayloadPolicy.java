package com.walmartlabs.concord.policyengine;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2022 Walmart Inc.
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RawPayloadPolicy {
    
    private final RawPayloadRule rule;

    public RawPayloadPolicy(RawPayloadRule rule) {
        this.rule = rule;
    }

    public CheckResult<RawPayloadRule, Long> check(Path p) throws IOException {
        if (rule == null || !Files.exists(p) || !Files.isRegularFile(p)) {
            return CheckResult.success();
        }

        List<CheckResult.Item<RawPayloadRule, Long>> deny = new ArrayList<>();

        long size = Files.size(p);

        if (size > rule.maxSizeInBytes()) {
            deny.add(new CheckResult.Item<>(rule, size, null));
        }

        return new CheckResult<>(Collections.emptyList(), deny);
    }
}
